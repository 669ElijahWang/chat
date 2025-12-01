import torch.nn as nn
import torch
import torch.nn.functional as F
import math
try:
    from einops import rearrange as _einops_rearrange
    def rearrange(x, pattern, **kwargs):
        return _einops_rearrange(x, pattern, **kwargs)
except Exception:
    def rearrange(x, pattern, **kwargs):
        head = kwargs.get('head')
        if pattern == 'b (head d) h w -> b head (h w) d':
            b, c, h, w = x.shape
            d = c // (head or 1)
            return x.view(b, head, d, h, w).permute(0, 1, 3, 4, 2).reshape(b, head, h * w, d)
        if pattern == 'b (head d) n -> b head n d':
            b, c, n = x.shape
            d = c // (head or 1)
            return x.view(b, head, d, n).permute(0, 1, 3, 2)
        if pattern == 'b head n d -> b n (head d)':
            b, head_val, n, d = x.shape
            return x.permute(0, 2, 1, 3).contiguous().view(b, n, head_val * d)
        if pattern == 'b c h w -> b (h w) c':
            b, c, h, w = x.shape
            return x.permute(0, 2, 3, 1).reshape(b, h * w, c)
        if pattern == 'b (h w) c -> b c h w':
            h = kwargs.get('h')
            b, hw, c = x.shape
            w = hw // (h or 1)
            return x.view(b, h, w, c).permute(0, 3, 1, 2)
        raise ImportError('einops is not installed and unsupported rearrange pattern: %s' % pattern)

class PreNorm(nn.Module):
    def __init__(self, dim, fn):
        super().__init__()
        self.norm = nn.LayerNorm(dim)
        self.fn = fn

    def forward(self, x, **kwargs):
        return self.fn(self.norm(x), **kwargs)


class FeedForward(nn.Module):
    def __init__(self, dim, hidden_dim, dropout = 0.):
        super().__init__()
        self.net = nn.Sequential(
            nn.Linear(dim, hidden_dim),
            nn.GELU(),
            nn.Dropout(dropout),
            nn.Linear(hidden_dim, dim),
            nn.Dropout(dropout)
        )
    def forward(self, x):
        return self.net(x)


class PPM(nn.Module):
    def __init__(self, pooling_sizes=(1, 3, 5)):
        super().__init__()
        self.layer = nn.ModuleList([nn.AdaptiveAvgPool2d(output_size=(size,size)) for size in pooling_sizes])

    def forward(self, feat):
        b, c, h, w = feat.shape
        output = [layer(feat).view(b, c, -1) for layer in self.layer]
        output = torch.cat(output, dim=-1)
        return output


# Efficient self attention
class ESA_layer(nn.Module):
    def __init__(self, dim, heads = 8, dim_head = 64, dropout = 0.):
        super().__init__()
        inner_dim = dim_head * heads
        project_out = not (heads == 1 and dim_head == dim)

        self.heads = heads
        self.scale = dim_head ** -0.5

        self.attend = nn.Softmax(dim=-1)
        self.to_qkv = nn.Conv2d(dim, inner_dim * 3, kernel_size=1, stride=1, padding=0, bias=False)
        self.ppm = PPM(pooling_sizes=(1, 3, 5))
        self.to_out = nn.Sequential(
            nn.Linear(inner_dim, dim),
            nn.Dropout(dropout)
        ) if project_out else nn.Identity()

    def forward(self, x):
        # input x (b, c, h, w)
        b, c, h, w = x.shape
        q, k, v = self.to_qkv(x).chunk(3, dim=1)  # q/k/v shape: (b, inner_dim, h, w)
        q = rearrange(q, 'b (head d) h w -> b head (h w) d', head=self.heads)   # q shape: (b, head, n_q, d)

        k, v = self.ppm(k), self.ppm(v)  # k/v shape: (b, inner_dim, n_kv)
        k = rearrange(k, 'b (head d) n -> b head n d', head=self.heads) # k shape: (b, head, n_kv, d)
        v = rearrange(v, 'b (head d) n -> b head n d', head=self.heads) # v shape: (b, head, n_kv, d)

        dots = torch.matmul(q, k.transpose(-1, -2)) * self.scale  # shape: (b, head, n_q, n_kv)

        attn = self.attend(dots)

        out = torch.matmul(attn, v) # shape: (b, head, n_q, d)
        out = rearrange(out, 'b head n d -> b n (head d)')
        return self.to_out(out)


class ESA_blcok(nn.Module):
    def __init__(self, dim, heads=8, dim_head=64, mlp_dim=512, dropout = 0.):
        super().__init__()
        self.ESAlayer = ESA_layer(dim, heads=heads, dim_head=dim_head, dropout=dropout)
        self.ff = PreNorm(dim, FeedForward(dim, mlp_dim, dropout = dropout))


    def forward(self, x):
        b, c, h, w = x.shape
        out = rearrange(x, 'b c h w -> b (h w) c')
        out = self.ESAlayer(x) + out
        out = self.ff(out) + out
        out = rearrange(out, 'b (h w) c -> b c h w', h=h)

        return out


def MaskAveragePooling(x, mask):
    mask = torch.sigmoid(mask)
    b, c, h, w = x.shape
    eps = 0.0005
    x_mask = x * mask
    h, w = x.shape[2], x.shape[3]
    area = F.avg_pool2d(mask, (h, w)) * h * w + eps
    x_feat = F.avg_pool2d(x_mask, (h, w)) * h * w / area
    x_feat = x_feat.view(b, c, -1)
    return x_feat


class PCA_layer(nn.Module):
    def __init__(self, dim, heads = 8, dim_head = 64, dropout = 0.):
        super().__init__()
        inner_dim = dim_head * heads
        project_out = not (heads == 1 and dim_head == dim)
        self.heads = heads
        self.scale = dim_head ** -0.5

        self.attend = nn.Softmax(dim=-1)
        self.to_qkv = nn.Conv2d(dim, inner_dim * 3, kernel_size=1, stride=1, padding=0, bias=False)
        self.to_out = nn.Sequential(
            nn.Linear(inner_dim, dim),
            nn.Dropout(dropout)
        ) if project_out else nn.Identity()


    def forward(self, x, mask):
        # input x (b, c, h, w)
        b, c, h, w = x.shape
        q, k, v = self.to_qkv(x).chunk(3, dim=1)  # q/k/v shape: (b, inner_dim, h, w)
        q = rearrange(q, 'b (head d) h w -> b head (h w) d', head=self.heads)  # q shape: (b, head, n_q, d)

        k, v = MaskAveragePooling(k, mask), MaskAveragePooling(v, mask)  # k/v shape: (b, inner_dim, 1)
        k = rearrange(k, 'b (head d) n -> b head n d', head=self.heads)  # k shape: (b, head, 1, d)
        v = rearrange(v, 'b (head d) n -> b head n d', head=self.heads)  # v shape: (b, head, 1, d)

        dots = torch.matmul(q, k.transpose(-1, -2)) * self.scale  # shape: (b, head, n_q, n_kv)

        attn = self.attend(dots)

        out = torch.matmul(attn, v)  # shape: (b, head, n_q, d)
        out = rearrange(out, 'b head n d -> b n (head d)')
        return self.to_out(out)


class PCA_blcok(nn.Module):
    def __init__(self, dim, heads=8, dim_head=64, mlp_dim=512, dropout = 0.):
        super().__init__()
        self.PCAlayer = PCA_layer(dim, heads=heads, dim_head=dim_head, dropout=dropout)
        self.ff = PreNorm(dim, FeedForward(dim, mlp_dim, dropout = dropout))

    def forward(self, x, mask):
        b, c, h, w = x.shape
        out = rearrange(x, 'b c h w -> b (h w) c')
        out = self.PCAlayer(x, mask) + out
        out = self.ff(out) + out
        out = rearrange(out, 'b (h w) c -> b c h w', h=h)

        return out

class SpatialAttentionModule(nn.Module):
    def __init__(self):
        super(SpatialAttentionModule, self).__init__()
        self.conv2d = nn.Conv2d(in_channels=2, out_channels=1, kernel_size=7, stride=1, padding=3)
        self.sigmoid = nn.Sigmoid()

    def forward(self, x):
        avgout = torch.mean(x, dim=1, keepdim=True)
        maxout, _ = torch.max(x, dim=1, keepdim=True)
        out = torch.cat([avgout, maxout], dim=1)
        out = self.sigmoid(self.conv2d(out))
        return out * x


class APA(nn.Module):
    def __init__(self, in_features, filters) -> None:
        super().__init__()

        self.skip = conv_block(in_features=in_features,
                               out_features=filters,
                               kernel_size=(1, 1),
                               padding=(0, 0),
                               norm_type='bn',
                               activation=False)
        self.c1 = conv_block(in_features=in_features,
                             out_features=filters,
                             kernel_size=(3, 3),
                             padding=(1, 1),
                             norm_type='bn',
                             activation=True)
        self.c2 = conv_block(in_features=filters,
                             out_features=filters,
                             kernel_size=(3, 3),
                             padding=(1, 1),
                             norm_type='bn',
                             activation=True)
        self.c3 = conv_block(in_features=filters,
                             out_features=filters,
                             kernel_size=(3, 3),
                             padding=(1, 1),
                             norm_type='bn',
                             activation=True)
        self.sa = SpatialAttentionModule()
        self.cn = ECA(filters)
        self.lga2 = LocalGlobalAttention(filters, 2)
        self.lga4 = LocalGlobalAttention(filters, 4)

        self.bn1 = nn.BatchNorm2d(filters)
        self.drop = nn.Dropout2d(0.1)
        self.relu = nn.ReLU()

        self.gelu = nn.GELU()

    def forward(self, x):
        x_skip = self.skip(x)
        x_lga2 = self.lga2(x_skip)
        x_lga4 = self.lga4(x_skip)
        x1 = self.c1(x)
        x2 = self.c2(x1)
        x3 = self.c3(x2)
        x = x1 + x2 + x3 + x_skip + x_lga2 + x_lga4
        x = self.cn(x)
        x = self.sa(x)
        x = self.drop(x)
        x = self.bn1(x)
        x = self.relu(x)
        return x


class LocalGlobalAttention(nn.Module):
    def __init__(self, output_dim, patch_size):
        super().__init__()
        self.output_dim = output_dim
        self.patch_size = patch_size
        self.mlp1 = nn.Linear(patch_size * patch_size, output_dim // 2)
        self.norm = nn.LayerNorm(output_dim // 2)
        self.mlp2 = nn.Linear(output_dim // 2, output_dim)
        self.conv = nn.Conv2d(output_dim, output_dim, kernel_size=1)
        self.prompt = torch.nn.parameter.Parameter(torch.randn(output_dim, requires_grad=True))
        self.top_down_transform = torch.nn.parameter.Parameter(torch.eye(output_dim), requires_grad=True)

    def forward(self, x):
        x = x.permute(0, 2, 3, 1)
        B, H, W, C = x.shape
        P = self.patch_size

        # Local branch
        local_patches = x.unfold(1, P, P).unfold(2, P, P)  # (B, H/P, W/P, P, P, C)
        local_patches = local_patches.reshape(B, -1, P * P, C)  # (B, H/P*W/P, P*P, C)
        local_patches = local_patches.mean(dim=-1)  # (B, H/P*W/P, P*P)

        local_patches = self.mlp1(local_patches)  # (B, H/P*W/P, input_dim // 2)
        local_patches = self.norm(local_patches)  # (B, H/P*W/P, input_dim // 2)
        local_patches = self.mlp2(local_patches)  # (B, H/P*W/P, output_dim)

        local_attention = F.softmax(local_patches, dim=-1)  # (B, H/P*W/P, output_dim)
        local_out = local_patches * local_attention  # (B, H/P*W/P, output_dim)

        cos_sim = F.normalize(local_out, dim=-1) @ F.normalize(self.prompt[None, ..., None], dim=1)  # B, N, 1
        mask = cos_sim.clamp(0, 1)
        local_out = local_out * mask
        local_out = local_out @ self.top_down_transform

        # Restore shapes
        local_out = local_out.reshape(B, H // P, W // P, self.output_dim)  # (B, H/P, W/P, output_dim)
        local_out = local_out.permute(0, 3, 1, 2)
        local_out = F.interpolate(local_out, size=(H, W), mode='bilinear', align_corners=False)
        output = self.conv(local_out)

        return output


class ECA(nn.Module):
    def __init__(self, in_channel, gamma=2, b=1):
        super(ECA, self).__init__()
        k = int(abs((math.log(in_channel, 2) + b) / gamma))
        kernel_size = k if k % 2 else k + 1
        padding = kernel_size // 2
        self.pool = nn.AdaptiveAvgPool2d(output_size=1)
        self.conv = nn.Sequential(
            nn.Conv1d(in_channels=1, out_channels=1, kernel_size=kernel_size, padding=padding, bias=False),
            nn.Sigmoid()
        )

    def forward(self, x):
        out = self.pool(x)
        out = out.view(x.size(0), 1, x.size(1))
        out = self.conv(out)
        out = out.view(x.size(0), x.size(1), 1, 1)
        return out * x


class conv_block(nn.Module):
    def __init__(self,
                 in_features,
                 out_features,
                 kernel_size=(3, 3),
                 stride=(1, 1),
                 padding=(1, 1),
                 dilation=(1, 1),
                 norm_type='bn',
                 activation=True,
                 use_bias=True,
                 groups=1
                 ):
        super().__init__()
        self.conv = nn.Conv2d(in_channels=in_features,
                              out_channels=out_features,
                              kernel_size=kernel_size,
                              stride=stride,
                              padding=padding,
                              dilation=dilation,
                              bias=use_bias,
                              groups=groups)

        self.norm_type = norm_type
        self.act = activation

        if self.norm_type == 'gn':
            self.norm = nn.GroupNorm(32 if out_features >= 32 else out_features, out_features)
        if self.norm_type == 'bn':
            self.norm = nn.BatchNorm2d(out_features)
        if self.act:
            # self.relu = nn.GELU()
            self.relu = nn.ReLU(inplace=False)

    def forward(self, x):
        x = self.conv(x)
        if self.norm_type is not None:
            x = self.norm(x)
        if self.act:
            x = self.relu(x)
        return x



