import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

def create_radial_glow(width, height, center_x, center_y, radius, color_rgb, max_alpha):
    glow = Image.new('RGBA', (width, height), (0, 0, 0, 0))
    glow_draw = ImageDraw.Draw(glow)
    steps = 35
    for i in range(steps, 0, -1):
        r = radius * (i / steps)
        alpha = int(max_alpha * (1 - (i / steps))**1.7)
        bbox = [center_x - r, center_y - r, center_x + r, center_y + r]
        glow_draw.ellipse(bbox, fill=(color_rgb[0], color_rgb[1], color_rgb[2], alpha))
    return glow.filter(ImageFilter.GaussianBlur(radius // 4))

def render_marketing_card(
    raw_path,
    output_path,
    chip_text,
    title_text,
    subtitle_text,
    accent_color=(0, 229, 255) # Electric cyan
):
    canvas_w, canvas_h = 1080, 2400
    canvas = Image.new('RGBA', (canvas_w, canvas_h), (8, 12, 20, 255))
    
    # 1. Background gradient (Rich dark cyber obsidian to deep slate)
    base_bg = Image.new('RGBA', (canvas_w, canvas_h))
    bg_draw = ImageDraw.Draw(base_bg)
    for y in range(canvas_h):
        factor = y / canvas_h
        r = int(7 + (13 - 7) * factor)
        g = int(10 + (22 - 10) * factor)
        b = int(18 + (38 - 18) * factor)
        bg_draw.line([(0, y), (canvas_w, y)], fill=(r, g, b, 255))
    canvas.alpha_composite(base_bg)
    
    # 2. Ambient radial glow behind phone
    glow_layer = create_radial_glow(canvas_w, canvas_h, 540, 1150, 480, accent_color, 40)
    canvas.alpha_composite(glow_layer)
    
    # Secondary top subtle glow
    top_glow = create_radial_glow(canvas_w, canvas_h, 540, 140, 300, (14, 165, 233), 25)
    canvas.alpha_composite(top_glow)

    # 3. Subtle High-Tech Cyber Grid
    grid_layer = Image.new('RGBA', (canvas_w, canvas_h), (0, 0, 0, 0))
    grid_draw = ImageDraw.Draw(grid_layer)
    grid_step = 80
    for x in range(0, canvas_w, grid_step):
        grid_draw.line([(x, 0), (x, canvas_h)], fill=(255, 255, 255, 8), width=1)
    for y in range(0, canvas_h, grid_step):
        grid_draw.line([(0, y), (canvas_w, y)], fill=(255, 255, 255, 8), width=1)
    canvas.alpha_composite(grid_layer)

    # Fonts
    font_bold_path = r'C:\Windows\Fonts\segoeuib.ttf'
    font_reg_path = r'C:\Windows\Fonts\segoeui.ttf'
    
    chip_font = ImageFont.truetype(font_bold_path, 26)
    title_font = ImageFont.truetype(font_bold_path, 52)
    sub_font = ImageFont.truetype(font_reg_path, 28)

    draw = ImageDraw.Draw(canvas)

    # 4. Chip Pill at top (y = 80)
    chip_bbox = draw.textbbox((0, 0), chip_text, font=chip_font)
    chip_w = chip_bbox[2] - chip_bbox[0]
    chip_h = chip_bbox[3] - chip_bbox[1]
    pill_padding_x = 28
    pill_padding_y = 12
    pill_w = chip_w + pill_padding_x * 2
    pill_h = chip_h + pill_padding_y * 2
    pill_x0 = (canvas_w - pill_w) // 2
    pill_y0 = 85
    pill_x1 = pill_x0 + pill_w
    pill_y1 = pill_y0 + pill_h

    # Chip background with subtle stroke
    draw.rounded_rectangle([pill_x0, pill_y0, pill_x1, pill_y1], radius=24, fill=(15, 23, 42, 225), outline=accent_color, width=2)
    draw.text((pill_x0 + pill_padding_x, pill_y0 + pill_padding_y - 2), chip_text, font=chip_font, fill=accent_color)

    # 5. Headline Text (y = 175)
    title_bbox = draw.textbbox((0, 0), title_text, font=title_font)
    title_w = title_bbox[2] - title_bbox[0]
    draw.text(((canvas_w - title_w) // 2, 175), title_text, font=title_font, fill=(255, 255, 255))

    # 6. Subtitle Text (y = 250)
    sub_bbox = draw.textbbox((0, 0), subtitle_text, font=sub_font)
    sub_w = sub_bbox[2] - sub_bbox[0]
    draw.text(((canvas_w - sub_w) // 2, 250), subtitle_text, font=sub_font, fill=(148, 163, 184))

    # 7. Device Frame Dimensions
    phone_w = 860
    phone_h = 1860
    phone_x0 = (canvas_w - phone_w) // 2 # 110
    phone_y0 = 345
    phone_x1 = phone_x0 + phone_w       # 970
    phone_y1 = phone_y0 + phone_h       # 2205

    bezel_radius = 56
    screen_border_margin = 12
    screen_x0 = phone_x0 + screen_border_margin
    screen_y0 = phone_y0 + screen_border_margin
    screen_x1 = phone_x1 - screen_border_margin
    screen_y1 = phone_y1 - screen_border_margin
    screen_w = screen_x1 - screen_x0
    screen_h = screen_y1 - screen_y0
    screen_radius = bezel_radius - 8

    # 8. Phone Drop Shadow Layer
    shadow_layer = Image.new('RGBA', (canvas_w, canvas_h), (0, 0, 0, 0))
    shadow_draw = ImageDraw.Draw(shadow_layer)
    shadow_draw.rounded_rectangle(
        [phone_x0 + 10, phone_y0 + 25, phone_x1 - 10, phone_y1 + 40],
        radius=bezel_radius,
        fill=(0, 0, 0, 180)
    )
    shadow_layer = shadow_layer.filter(ImageFilter.GaussianBlur(30))
    canvas.alpha_composite(shadow_layer)

    # 9. Outer Bezel (Sleek Dark Titanium Frame)
    bezel_layer = Image.new('RGBA', (canvas_w, canvas_h), (0, 0, 0, 0))
    bezel_draw = ImageDraw.Draw(bezel_layer)
    bezel_draw.rounded_rectangle(
        [phone_x0, phone_y0, phone_x1, phone_y1],
        radius=bezel_radius,
        fill=(17, 24, 39, 255),
        outline=(51, 65, 85, 255),
        width=3
    )
    # Subtle cyber accent rim on edges
    bezel_draw.rounded_rectangle(
        [phone_x0 + 1, phone_y0 + 1, phone_x1 - 1, phone_y1 - 1],
        radius=bezel_radius,
        outline=(accent_color[0], accent_color[1], accent_color[2], 75),
        width=1
    )
    canvas.alpha_composite(bezel_layer)

    # 10. Inner Screen Screenshot
    raw_img = Image.open(raw_path).convert('RGBA')
    scaled_screen = raw_img.resize((screen_w, screen_h), Image.Resampling.LANCZOS)

    # Screen Mask for rounded corners
    screen_mask = Image.new('L', (screen_w, screen_h), 0)
    mask_draw = ImageDraw.Draw(screen_mask)
    mask_draw.rounded_rectangle([0, 0, screen_w, screen_h], radius=screen_radius, fill=255)

    # Composite screenshot through RGBA canvas layer to ensure proper alpha blending
    screen_canvas = Image.new('RGBA', (canvas_w, canvas_h), (0, 0, 0, 0))
    screen_canvas.paste(scaled_screen, (screen_x0, screen_y0), screen_mask)
    canvas.alpha_composite(screen_canvas)

    # 11. Subtle Edge Highlight around Screen
    edge_layer = Image.new('RGBA', (canvas_w, canvas_h), (0, 0, 0, 0))
    edge_draw = ImageDraw.Draw(edge_layer)
    edge_draw.rounded_rectangle(
        [screen_x0, screen_y0, screen_x1, screen_y1],
        radius=screen_radius,
        outline=(30, 41, 59, 160),
        width=2
    )
    canvas.alpha_composite(edge_layer)

    # 12. Punch-hole camera dot at top center
    camera_layer = Image.new('RGBA', (canvas_w, canvas_h), (0, 0, 0, 0))
    cam_draw = ImageDraw.Draw(camera_layer)
    cam_cx = canvas_w // 2
    cam_cy = screen_y0 + 26
    cam_radius = 8
    cam_draw.ellipse(
        [cam_cx - cam_radius, cam_cy - cam_radius, cam_cx + cam_radius, cam_cy + cam_radius],
        fill=(8, 12, 20, 255),
        outline=(30, 41, 59, 220),
        width=2
    )
    # Tiny optical lens reflection
    cam_draw.ellipse([cam_cx - 3, cam_cy - 3, cam_cx - 1, cam_cy - 1], fill=(56, 189, 248, 140))
    canvas.alpha_composite(camera_layer)

    # Convert to standard 24-bit RGB PNG (Play Store requirement)
    final_card = canvas.convert('RGB')
    final_card.save(output_path, format='PNG')
    size_kb = os.path.getsize(output_path) / 1024
    print(f"Generated: {os.path.basename(output_path)} ({size_kb:.1f} KB)")

cards = [
    {
        "raw": "01_shield_home.png",
        "out": "01_shield_home_framed.png",
        "chip": "LOCAL VPN • ZERO ROOT",
        "title": "SYSTEM-WIDE PROTECTION",
        "sub": "Block Ads, Trackers & Telemetry In All Apps",
        "accent": (0, 229, 255) # Cyan
    },
    {
        "raw": "02_activity_logs.png",
        "out": "02_activity_logs_framed.png",
        "chip": "REAL-TIME TRANSPARENCY",
        "title": "LIVE DNS QUERY STREAM",
        "sub": "Inspect Blocked & Allowed Requests with Latency",
        "accent": (0, 230, 118) # Neon Green
    },
    {
        "raw": "03_filters_rules.png",
        "out": "03_filters_rules_framed.png",
        "chip": "DYNAMIC BLOCKLISTS",
        "title": "30+ CURATED FILTER LISTS",
        "sub": "AdGuard, StevenBlack & Custom Domain Filtering",
        "accent": (56, 189, 248) # Sky Blue
    },
    {
        "raw": "04_apps_firewall.png",
        "out": "04_apps_firewall_framed.png",
        "chip": "APP-LEVEL CONTROL",
        "title": "PER-APP NETWORK FIREWALL",
        "sub": "Bypass or Protect Specific Apps Seamlessly",
        "accent": (168, 85, 247) # Electric Purple
    },
    {
        "raw": "05_settings.png",
        "out": "05_settings_framed.png",
        "chip": "ENCRYPTED UPSTREAM",
        "title": "NEXT-GEN ENCRYPTED DNS",
        "sub": "DNS-over-HTTPS (DoH) & DNS-over-TLS (DoT)",
        "accent": (0, 229, 255) # Cyan
    },
    {
        "raw": "06_analytics.png",
        "out": "06_analytics_framed.png",
        "chip": "PRIVACY INTELLIGENCE",
        "title": "VISUAL TRAFFIC ANALYTICS",
        "sub": "Track Saved Bandwidth & Top Querying Apps",
        "accent": (245, 158, 11) # Amber / Gold
    },
    {
        "raw": "07_browser.png",
        "out": "07_browser_framed.png",
        "chip": "LIGHTNING FAST",
        "title": "AD-FREE BUILT-IN BROWSER",
        "sub": "Clean Surfing with Automatic Script Blocking",
        "accent": (236, 72, 153) # Pink / Magenta
    },
    {
        "raw": "08_account_sync.png",
        "out": "08_account_sync_framed.png",
        "chip": "CLOUD BACKUP & RESTORE",
        "title": "GOOGLE DRIVE CLOUD SYNC",
        "sub": "Backup Custom Filters & Sync Multiple Devices",
        "accent": (0, 230, 118) # Emerald Green
    }
]

raw_dir = r"c:\Projects\AndroidStudioProjects\AdBlocker\playstore_assets\screenshots_raw"
out_dir = r"c:\Projects\AndroidStudioProjects\AdBlocker\playstore_assets\screenshots_framed"
os.makedirs(out_dir, exist_ok=True)

for card in cards:
    raw_p = os.path.join(raw_dir, card["raw"])
    out_p = os.path.join(out_dir, card["out"])
    if os.path.exists(raw_p):
        render_marketing_card(
            raw_path=raw_p,
            output_path=out_p,
            chip_text=card["chip"],
            title_text=card["title"],
            subtitle_text=card["sub"],
            accent_color=card["accent"]
        )
    else:
        print(f"Skipping missing raw file: {raw_p}")
