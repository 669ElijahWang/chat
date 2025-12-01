import argparse
import os
import sys
import torch
import torch.nn.functional as F
import numpy as np
from PIL import Image
from collections import OrderedDict
from MdfraNet import mdfraNet
import tempfile
import zipfile

def load_image(path, size):
    img = Image.open(path).convert('RGB')
    w, h = img.size
    img_resized = img.resize((size, size), Image.BILINEAR)
    arr = np.array(img_resized).astype(np.float32) / 255.0
    mean = np.array([0.485, 0.456, 0.406], dtype=np.float32)
    std = np.array([0.229, 0.224, 0.225], dtype=np.float32)
    arr = (arr - mean) / std
    arr = arr.transpose(2, 0, 1)
    try:
        tensor = torch.from_numpy(arr).unsqueeze(0)
    except Exception:
        tensor = torch.tensor(arr.tolist(), dtype=torch.float32).unsqueeze(0)
    return tensor, (w, h), img

def to_uint8_mask(pred):
    pred = pred.squeeze()
    pred = (pred - pred.min()) / (pred.max() - pred.min() + 1e-8)
    mask = (pred * 255).cpu().numpy().astype(np.uint8)
    return mask

def save_overlay(original_img, mask_uint8, out_path):
    orig = original_img.copy()
    mask_img = Image.fromarray(mask_uint8, mode='L').resize(orig.size, Image.NEAREST)
    red = Image.new('RGB', orig.size, (255, 0, 0))
    overlay = Image.composite(red, orig, mask_img)
    overlay.save(out_path)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--image', type=str, required=True)
    parser.add_argument('--out_dir', type=str, required=True)
    parser.add_argument('--testsize', type=int, default=352)
    parser.add_argument('--pth_path', type=str, default='./snapshots/best/MdfraNet-best.pth')
    args = parser.parse_args()

    os.makedirs(args.out_dir, exist_ok=True)
    device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')

    print("python_version=%s" % sys.version.replace('\n',' '))
    print("torch_version=%s" % torch.__version__)
    try:
        with open(args.pth_path, 'rb') as f:
            head = f.read(64)
        if head.startswith(b'version ') or b'git-lfs' in head:
            print("weights_pointer_detected=true")
        elif head.startswith(b'PK'):
            print("weights_zip_format=true")
    except Exception:
        pass

    def resolve_weights_path(orig_path):
        try:
            with open(orig_path, 'rb') as f:
                head = f.read(4)
            if head.startswith(b'PK'):
                with zipfile.ZipFile(orig_path, 'r') as zf:
                    names = zf.namelist()
                    has_torch_zip_markers = any(n.endswith('data.pkl') for n in names) or any('storages' in n for n in names) or any(n.endswith('version') for n in names)
                    if has_torch_zip_markers:
                        return orig_path
                    candidates = [n for n in names if n.lower().endswith(('.pth', '.pt', '.ckpt'))]
                    if candidates:
                        target = candidates[0]
                        fd, tmp_path = tempfile.mkstemp(suffix='_' + os.path.basename(target).replace('/', '_'))
                        os.close(fd)
                        with open(tmp_path, 'wb') as out:
                            out.write(zf.read(target))
                        print("weights_zip_extract=%s" % target.replace('\n',' '))
                        return tmp_path
        except Exception:
            pass
        return orig_path

    model = mdfraNet()
    def try_load(path):
        try:
            return torch.load(path, map_location=device, weights_only=True)
        except TypeError:
            try:
                return torch.load(path, map_location=device)
            except Exception as e2:
                print("weights_load_error=%s" % (str(e2).replace('\n',' ')))
                raise
        except Exception as e1:
            try:
                return torch.jit.load(path, map_location=device)
            except Exception:
                print("weights_load_error=%s" % (str(e1).replace('\n',' ')))
                raise

    maybe_path = resolve_weights_path(args.pth_path)
    weights = try_load(maybe_path)

    if hasattr(weights, 'state_dict') and callable(getattr(weights, 'state_dict')):
        sd = weights.state_dict()
    elif isinstance(weights, dict) and 'state_dict' in weights:
        sd = weights['state_dict']
    elif isinstance(weights, dict) and 'model' in weights and isinstance(weights['model'], dict):
        sd = weights['model']
    elif isinstance(weights, dict):
        sd = weights
    else:
        sd = {}

    new_state_dict = OrderedDict()
    for k, v in sd.items():
        if 'total_ops' not in k and 'total_params' not in k:
            new_state_dict[k.replace('module.', '')] = v
    model.load_state_dict(new_state_dict, strict=False)
    model.to(device)
    model.eval()

    image_tensor, orig_size, orig_img = load_image(args.image, args.testsize)
    image_tensor = image_tensor.to(device)

    with torch.no_grad():
        outputs = model(image_tensor)
        if isinstance(outputs, (list, tuple)):
            res = outputs[0]
            for o in outputs[1:]:
                try:
                    res = res + o
                except Exception:
                    pass
        else:
            res = outputs
        res = F.interpolate(res, size=(orig_size[1], orig_size[0]), mode='bilinear', align_corners=False)
        res = torch.sigmoid(res)
    mask_uint8 = to_uint8_mask(res)

    mask_path = os.path.join(args.out_dir, 'mask.png')
    overlay_path = os.path.join(args.out_dir, 'overlay.png')
    Image.fromarray(mask_uint8).save(mask_path)
    save_overlay(orig_img, mask_uint8, overlay_path)

    print('{"mask_path":"%s","overlay_path":"%s"}' % (mask_path.replace('\\','/'), overlay_path.replace('\\','/')))

if __name__ == '__main__':
    main()
