import torch
import torch.nn as nn
import torch.nn.functional as F
from pretrain.Res2Net_v1b import res2net50_v1b_26w_4s, res2net101_v1b_26w_4s
from lib.MSMB import MSMBModule
from lib.pd import aggregation, Conv

from lib.HMCRA import APA, ESA_blcok, PCA_blcok


class mdfraNet(nn.Module):
    def __init__(self, channel=32):
        super().__init__()

        # ---- ResNet Backbone ----
        self.resnet = res2net101_v1b_26w_4s(pretrained=False)

        # Receptive Field Block
        self.rfb2_1 = Conv(512, 32, 3, 1, padding=1, bn_acti=True)
        self.rfb3_1 = Conv(1024, 32, 3, 1, padding=1, bn_acti=True)
        self.rfb4_1 = Conv(2048, 32, 3, 1, padding=1, bn_acti=True)

        # Partial Decoder
        self.agg1 = aggregation(channel)

        self.MSMB_1 = MSMBModule(32, d=8)
        self.MSMB_2 = MSMBModule(32, d=8)
        self.MSMB_3 = MSMBModule(32, d=8)
        ###### dilation rate 4, 62.8

        self.ra1_conv1 = Conv(32, 32, 3, 1, padding=1, bn_acti=True)
        self.ra1_conv2 = Conv(32, 32, 3, 1, padding=1, bn_acti=True)
        self.ra1_conv3 = Conv(32, 1, 3, 1, padding=1, bn_acti=True)

        self.ra2_conv1 = Conv(32, 32, 3, 1, padding=1, bn_acti=True)
        self.ra2_conv2 = Conv(32, 32, 3, 1, padding=1, bn_acti=True)
        self.ra2_conv3 = Conv(32, 1, 3, 1, padding=1, bn_acti=True)

        self.ra3_conv1 = Conv(32, 32, 3, 1, padding=1, bn_acti=True)
        self.ra3_conv2 = Conv(32, 32, 3, 1, padding=1, bn_acti=True)
        self.ra3_conv3 = Conv(32, 1, 3, 1, padding=1, bn_acti=True)

        self.conv = Conv(32, 1, 3, 1, padding=1, bn_acti=True)

        self.conv1 = nn.Conv2d(32, 32, kernel_size=4, stride=4, padding=0)
        self.conv2 = nn.Conv2d(32, 32, kernel_size=3, stride=2, padding=1)

        self.apa = APA(32, 32)

        self.esa = ESA_blcok(32)
        self.pca = PCA_blcok(32)

    def forward(self, x):
        x = self.resnet.conv1(x)
        x = self.resnet.bn1(x)
        x = self.resnet.relu(x)
        x = self.resnet.maxpool(x)

        # ----------- low-level features -------------

        x1 = self.resnet.layer1(x)
        print(x1.shape)
        x2 = self.resnet.layer2(x1)
        print(x2.shape)

        x3 = self.resnet.layer3(x2)
        print(x3.shape)
        x4 = self.resnet.layer4(x3)
        print(x4.shape)

        x2_rfb = self.rfb2_1(x2)
        x3_rfb = self.rfb3_1(x3)
        x4_rfb = self.rfb4_1(x4)
        print(x4_rfb.shape)
        print(x3_rfb.shape)
        print(x2_rfb.shape)

        decoder_1 = self.agg1(x4_rfb, x3_rfb, x2_rfb)
        lateral_map_1 = F.interpolate(decoder_1, scale_factor=8, mode='bilinear')

        # ------------------- atten-one -----------------------
        decoder_2 = F.interpolate(decoder_1, scale_factor=0.25, mode='bilinear')
        msmb_out_1 = self.MSMB_3(x4_rfb)
        msmb_out_1 = self.esa(msmb_out_1)
        decoder_2_ra = -1 * (torch.sigmoid(decoder_2)) + 1
        decoder_2_ra = self.pca(msmb_out_1, decoder_2_ra)
        aa_atten_3 = self.apa(msmb_out_1)
        aa_atten_3_o = decoder_2_ra.expand(-1, 32, -1, -1).mul(aa_atten_3)

        ra_3 = self.ra3_conv1(aa_atten_3_o)
        ra_3 = self.ra3_conv2(ra_3)
        ra_3 = self.ra3_conv3(ra_3)

        x_3 = ra_3 + decoder_2
        lateral_map_2 = F.interpolate(x_3, scale_factor=32, mode='bilinear')

        # ------------------- atten-two -----------------------
        decoder_3 = F.interpolate(x_3, scale_factor=2, mode='bilinear')
        msmb_out_2 = self.MSMB_2(x3_rfb)
        msmb_out_2 = self.esa(msmb_out_2)
        decoder_3_ra = -1 * (torch.sigmoid(decoder_3)) + 1
        decoder_3_ra = self.pca(msmb_out_2, decoder_3_ra)
        aa_atten_2 = self.apa(msmb_out_2)
        aa_atten_2_o = decoder_3_ra.expand(-1, 32, -1, -1).mul(aa_atten_2)

        ra_2 = self.ra2_conv1(aa_atten_2_o)
        ra_2 = self.ra2_conv2(ra_2)
        ra_2 = self.ra2_conv3(ra_2)

        x_2 = ra_2 + decoder_3
        lateral_map_3 = F.interpolate(x_2, scale_factor=16, mode='bilinear')

        # ------------------- atten-three -----------------------
        decoder_4 = F.interpolate(x_2, scale_factor=2, mode='bilinear')
        msmb_out_3 = self.MSMB_1(x2_rfb)
        msmb_out_3 = self.esa(msmb_out_3)
        decoder_4_ra = -1 * (torch.sigmoid(decoder_4)) + 1
        decoder_4_ra = self.pca(msmb_out_3, decoder_4_ra)
        aa_atten_1 = self.apa(msmb_out_3)
        aa_atten_1_o = decoder_4_ra.expand(-1, 32, -1, -1).mul(aa_atten_1)

        ra_1 = self.ra1_conv1(aa_atten_1_o)
        ra_1 = self.ra1_conv2(ra_1)
        ra_1 = self.ra1_conv3(ra_1)

        x_1 = ra_1 + decoder_4
        lateral_map_5 = F.interpolate(x_1, scale_factor=8, mode='bilinear')


        return lateral_map_5, lateral_map_1


if __name__ == '__main__':
    ras = mdfraNet().cuda()
    input_tensor = torch.randn(1, 3, 352, 352).cuda()

    out = ras(input_tensor)
