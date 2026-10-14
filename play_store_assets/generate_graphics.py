import math
import os
from PIL import Image, ImageDraw, ImageFont

OUTPUT_DIR = os.path.dirname(os.path.abspath(__file__))

def create_radial_gradient(width, height, center_color, outer_color):
    img = Image.new("RGBA", (width, height), outer_color)
    draw = ImageDraw.Draw(img)
    cx, cy = width // 2, height // 2
    max_radius = math.hypot(cx, cy)

    steps = 60
    for i in range(steps, 0, -1):
        r = max_radius * (i / steps)
        factor = i / steps
        cr = int(center_color[0] * (1 - factor) + outer_color[0] * factor)
        cg = int(center_color[1] * (1 - factor) + outer_color[1] * factor)
        cb = int(center_color[2] * (1 - factor) + outer_color[2] * factor)
        draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(cr, cg, cb, 255))
    return img

def generate_512_icon():
    width, height = 512, 512
    img = create_radial_gradient(width, height, (30, 45, 80), (15, 23, 42))
    draw = ImageDraw.Draw(img)

    pad = 20
    draw.rounded_rectangle([pad, pad, width - pad, height - pad], radius=40, outline=(255, 107, 107, 220), width=6)

    # 2x2 Grid representation inside icon
    start_x, start_y = 100, 100
    step = 150

    # Box fills
    draw.rounded_rectangle([start_x + 10, start_y + 10, start_x + step - 10, start_y + step - 10], radius=15, fill=(255, 107, 107, 80))
    draw.rounded_rectangle([start_x + step + 10, start_y + step + 10, start_x + step * 2 - 10, start_y + step * 2 - 10], radius=15, fill=(34, 184, 167, 80))

    # Ink lines
    draw.line([(start_x, start_y), (start_x + step, start_y)], fill=(255, 107, 107), width=12)
    draw.line([(start_x, start_y), (start_x, start_y + step)], fill=(255, 107, 107), width=12)
    draw.line([(start_x + step, start_y), (start_x + step, start_y + step)], fill=(255, 107, 107), width=12)
    draw.line([(start_x, start_y + step), (start_x + step, start_y + step)], fill=(255, 107, 107), width=12)

    # Dots
    for r in range(3):
        for c in range(3):
            dx = start_x + c * step
            dy = start_y + r * step
            draw.ellipse([dx - 14, dy - 14, dx + 14, dy + 14], fill=(23, 35, 60))
            draw.ellipse([dx - 5, dy - 5, dx + 5, dy + 5], fill=(255, 255, 255, 200))

    try:
        font = ImageFont.truetype("Arial.ttf", 36)
    except:
        font = ImageFont.load_default()

    text = "CONNECT FOUR"
    bbox = draw.textbbox((0, 0), text, font=font)
    tw = bbox[2] - bbox[0]
    draw.text(((width - tw) // 2, height - 70), text, fill=(255, 107, 107), font=font)

    img.save(os.path.join(OUTPUT_DIR, "store_icon_512x512.png"))
    print("Generated Connect Four store_icon_512x512.png")

def generate_feature_graphic():
    width, height = 1024, 500
    img = create_radial_gradient(width, height, (30, 45, 80), (15, 23, 42))
    draw = ImageDraw.Draw(img)

    try:
        title_font = ImageFont.truetype("Arial.ttf", 64)
        sub_font = ImageFont.truetype("Arial.ttf", 28)
    except:
        title_font = ImageFont.load_default()
        sub_font = ImageFont.load_default()

    t_text = "CONNECT FOUR"
    s_text = "Tactile 4-In-A-Row Strategy Game"

    t_bbox = draw.textbbox((0, 0), t_text, font=title_font)
    tw = t_bbox[2] - t_bbox[0]
    draw.text(((width - tw) // 2, 170), t_text, fill=(255, 107, 107), font=title_font)

    s_bbox = draw.textbbox((0, 0), s_text, font=sub_font)
    sw = s_bbox[2] - s_bbox[0]
    draw.text(((width - sw) // 2, 260), s_text, fill=(34, 184, 167), font=sub_font)

    img.save(os.path.join(OUTPUT_DIR, "feature_graphic_1024x500.png"))
    print("Generated Connect Four feature_graphic_1024x500.png")

def main():
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    generate_512_icon()
    generate_feature_graphic()
    print("All Connect Four Play Store graphics generated successfully!")

if __name__ == "__main__":
    main()
