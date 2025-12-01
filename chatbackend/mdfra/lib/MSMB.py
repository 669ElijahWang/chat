import torch
import torch.nn as nn


# 定义MBConvBlock和SEBlock
class SEBlock(nn.Module):
    def __init__(self, in_channels, reduction=4):
        super(SEBlock, self).__init__()
        self.avg_pool = nn.AdaptiveAvgPool2d(1)
        self.fc1 = nn.Conv2d(in_channels, in_channels // reduction, kernel_size=1)
        self.fc2 = nn.Conv2d(in_channels // reduction, in_channels, kernel_size=1)

    def forward(self, x):
        out = self.avg_pool(x)
        out = nn.ReLU(inplace=True)(self.fc1(out))
        out = nn.Sigmoid()(self.fc2(out))
        return x * out


class MBConvBlock(nn.Module):
    def __init__(self, in_channels, out_channels, expand_ratio, stride, kernel_size):
        super(MBConvBlock, self).__init__()
        hidden_dim = in_channels * expand_ratio
        self.use_residual = (in_channels == out_channels) and (stride == 1)
        self.expand_conv = nn.Conv2d(in_channels, hidden_dim, kernel_size=1, bias=False)
        self.bn0 = nn.BatchNorm2d(hidden_dim)
        self.depthwise_conv = nn.Conv2d(hidden_dim, hidden_dim, kernel_size=kernel_size, stride=stride,
                                        padding=kernel_size // 2, groups=hidden_dim, bias=False)
        self.bn1 = nn.BatchNorm2d(hidden_dim)
        self.se = SEBlock(hidden_dim)
        self.project_conv = nn.Conv2d(hidden_dim, out_channels, kernel_size=1, bias=False)
        self.bn2 = nn.BatchNorm2d(out_channels)

    def forward(self, x):
        out = self.expand_conv(x)
        out = self.bn0(out)
        out = nn.ReLU6(inplace=True)(out)
        out = self.depthwise_conv(out)
        out = self.bn1(out)
        out = nn.ReLU6(inplace=True)(out)
        out = self.se(out)
        out = self.project_conv(out)
        out = self.bn2(out)
        if self.use_residual:
            out += x
        return out


# 替换深度可分离空洞卷积的CFPModule
class MSMBModule(nn.Module):
    def __init__(self, nIn, d=1, KSize=3, dkSize=3):
        super(MSMBModule, self).__init__()
        self.bn_relu_1 = BNPReLU(nIn)
        self.bn_relu_2 = BNPReLU2(nIn)
        self.conv1x1_1 = Conv(nIn, nIn // 4, KSize, 1, padding=1, bn_acti=True)
        # 使用MBConvBlock替代深度可分离空洞卷积
        self.mbconv1_1 = MBConvBlock(nIn // 4, nIn // 16, expand_ratio=4, stride=1, kernel_size=dkSize)
        self.mbconv1_2 = MBConvBlock(nIn // 16, nIn // 16, expand_ratio=4, stride=1, kernel_size=dkSize)
        self.mbconv1_3 = MBConvBlock(nIn // 16, nIn // 8, expand_ratio=4, stride=1, kernel_size=dkSize)


        self.mbconv2_1 = MBConvBlock(nIn // 4, nIn // 16, expand_ratio=4, stride=1, kernel_size=dkSize)
        self.mbconv2_2 = MBConvBlock(nIn // 16, nIn // 16, expand_ratio=4, stride=1, kernel_size=dkSize)
        self.mbconv2_3 = MBConvBlock(nIn // 16, nIn // 8, expand_ratio=4, stride=1, kernel_size=dkSize)

        self.mbconv3_1 = MBConvBlock(nIn // 4, nIn // 8, expand_ratio=4, stride=1, kernel_size=dkSize)
        self.mbconv3_2 = MBConvBlock(nIn // 8, nIn // 8, expand_ratio=4, stride=1, kernel_size=dkSize)
        self.mbconv3_3 = MBConvBlock(nIn // 8, nIn // 4, expand_ratio=4, stride=1, kernel_size=dkSize)

        self.conv1x1 = Conv(nIn, nIn, 1, 1, padding=0, bn_acti=False)

    def forward(self, input):
        inp = self.bn_relu_1(input)
        inp = self.conv1x1_1(inp)

        o1_1 = self.mbconv1_1(inp)
        o1_2 = self.mbconv1_2(o1_1)
        o1_3 = self.mbconv1_3(o1_2)

        o2_1 = self.mbconv2_1(inp)
        o2_2 = self.mbconv2_2(o2_1)
        o2_3 = self.mbconv2_3(o2_2)

        o3_1 = self.mbconv3_1(inp)
        o3_2 = self.mbconv3_2(o3_1)
        o3_3 = self.mbconv3_3(o3_2)

        output1 = torch.cat([o1_1, o1_2, o1_3], 1)
        output2 = torch.cat([o2_1, o2_2, o2_3], 1)
        output3 = torch.cat([o3_1, o3_2, o3_3], 1)

        output = torch.cat([output1, output2, output3], 1)

        output = self.bn_relu_2(output)
        output = self.conv1x1(output)

        return output + input


# 定义BNPReLU, BNPReLU2 和 Conv 类
class BNPReLU(nn.Module):
    def __init__(self, nOut):
        super(BNPReLU, self).__init__()
        self.bn = nn.BatchNorm2d(nOut)
        self.act = nn.PReLU(nOut)

    def forward(self, x):
        return self.act(self.bn(x))


class BNPReLU2(nn.Module):
    def __init__(self, nOut):
        super(BNPReLU2, self).__init__()
        self.bn = nn.BatchNorm2d(nOut)
        self.act = nn.PReLU(nOut)

    def forward(self, x):
        return self.act(self.bn(x))


class Conv(nn.Module):
    def __init__(self, in_channels, out_channels, kernel_size, stride=1, padding=0, dilation=1, groups=1,
                 bn_acti=False):
        super(Conv, self).__init__()
        self.conv = nn.Conv2d(in_channels, out_channels, kernel_size, stride, padding, dilation, groups, bias=False)
        self.bn_acti = bn_acti
        if self.bn_acti:
            self.bn = nn.BatchNorm2d(out_channels)
            self.act = nn.ReLU(inplace=True)

    def forward(self, x):
        x = self.conv(x)
        if self.bn_acti:
            x = self.bn(x)
            x = self.act(x)
        return x
