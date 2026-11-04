//! F8Q's background: a mesh gradient. Soft spots of colour with no edges drift over a plain base
//! and melt into each other. GPUI has no radial gradient, so each spot is a small picture of a
//! smooth falloff, scaled up.
use gpui::{div, img, prelude::*, px, rgb, Context, Div, Hsla, Image, ImageFormat, Rgba, Window};
use std::sync::Arc;
use std::time::{Duration, Instant};

pub const BASE_DARK: u32 = 0x080808;
pub const BASE_LIGHT: u32 = 0xf4f4f4;
/// Thirty frames a second: enough for slow movement, and each redraws this view alone.
const FRAME: Duration = Duration::from_millis(33);
/// The spot's picture: a smooth curve survives being scaled up.
const SPOT: u32 = 160;

/// One spot's path: where it circles as shares of the window, how far, how fast, its size as a
/// share of the window's width, and how far round the colour wheel from the accent it sits.
struct Glow {
    x: f32,
    y: f32,
    reach: f32,
    speed: f32,
    phase: f32,
    size: f32,
    hue: f32,
}

const GLOWS: [Glow; 5] = [
    Glow { x: 0.8, y: 0.15, reach: 0.3, speed: 0.34, phase: 0., size: 1.15, hue: 0. },
    Glow { x: 0.2, y: 0.85, reach: 0.34, speed: 0.27, phase: 2.1, size: 1.3, hue: 0.14 },
    Glow { x: 0.65, y: 0.8, reach: 0.38, speed: 0.22, phase: 4.2, size: 1.0, hue: -0.1 },
    Glow { x: 0.15, y: 0.2, reach: 0.26, speed: 0.31, phase: 1.1, size: 0.95, hue: 0.07 },
    Glow { x: 0.5, y: 0.45, reach: 0.4, speed: 0.17, phase: 3.3, size: 0.8, hue: -0.05 },
];

fn channel(color: Hsla) -> [u8; 3] {
    let c = Rgba::from(color);
    [c.r, c.g, c.b].map(|v| (v.clamp(0., 1.) * 255.) as u8)
}

/// A spot of `color`: strongest in the middle, nothing at all at the edge of the picture.
fn spot(color: Hsla, strength: f32) -> Arc<Image> {
    let [r, g, b] = channel(color);
    let mid = (SPOT as f32 - 1.) / 2.;
    let picture = image::RgbaImage::from_fn(SPOT, SPOT, |x, y| {
        let (dx, dy) = ((x as f32 - mid) / mid, (y as f32 - mid) / mid);
        let d = (dx * dx + dy * dy).sqrt().min(1.);
        // Smoothstep, twice: no visible rim where it reaches zero.
        let s = 1. - d * d * (3. - 2. * d);
        let a = s * s * strength;
        image::Rgba([r, g, b, (a * 255.) as u8])
    });
    let mut bytes = Vec::new();
    let encoded = picture.write_to(
        &mut std::io::Cursor::new(&mut bytes),
        image::ImageFormat::Png,
    );
    debug_assert!(encoded.is_ok());
    Arc::new(Image::from_bytes(ImageFormat::Png, bytes))
}

/// The spots' pictures for an accent, in [`GLOWS`]' order.
pub fn spots(dark: bool, accent: u32) -> Vec<Arc<Image>> {
    let base: Hsla = Rgba::from(rgb(if accent==0xffffff && !dark {0x888888} else {accent})).into();
    let strength = if accent == 0xffffff { 0.20 } else if dark { 0.42 } else { 0.5 };
    GLOWS
        .iter()
        .map(|g| {
            let mut color = base;
            color.h = (color.h + g.hue).rem_euclid(1.);
            spot(color, strength)
        })
        .collect()
}

/// The base and the spots `time` seconds in, for a window `width` by `height`.
pub fn scene(time: f32, width: f32, height: f32, dark: bool, spots: &[Arc<Image>]) -> Div {
    let layers = GLOWS.iter().zip(spots).map(|(g, picture)| {
        let side = g.size * width;
        let t = time * g.speed + g.phase;
        let x = (g.x + g.reach * t.sin()) * width - side / 2.;
        let y = (g.y + g.reach * (t * 1.37).cos()) * height - side / 2.;
        // Scaling up samples past the picture's edge and draws a thin line there: the outermost
        // part, transparent anyway, is cut off.
        let bleed = side * 0.04;
        div()
            .absolute()
            .left(px(x))
            .top(px(y))
            .size(px(side))
            .overflow_hidden()
            .child(
                img(picture.clone())
                    .absolute()
                    .left(px(-bleed))
                    .top(px(-bleed))
                    .size(px(side + bleed * 2.)),
            )
    });
    div()
        .absolute()
        .top_0()
        .left_0()
        .size_full()
        .overflow_hidden()
        .bg(rgb(if dark { BASE_DARK } else { BASE_LIGHT }))
        .children(layers)
}

pub struct Aurora {
    started: Instant,
    look: (bool, u32),
    spots: Vec<Arc<Image>>,
}

impl Aurora {
    pub fn new(cx: &mut Context<Self>) -> Self {
        cx.spawn(async move |this, cx| loop {
            cx.background_executor().timer(FRAME).await;
            if this.update(cx, |_, cx| cx.notify()).is_err() {
                break;
            }
        })
        .detach();
        let look = (true, 0xffffff);
        Self {
            started: Instant::now(),
            look,
            spots: spots(look.0, look.1),
        }
    }

    pub fn set_look(&mut self, dark: bool, accent: u32) {
        if self.look != (dark, accent) {
            self.look = (dark, accent);
            self.spots = spots(dark, accent);
        }
    }
}

impl Render for Aurora {
    fn render(&mut self, window: &mut Window, _: &mut Context<Self>) -> impl IntoElement {
        let viewport = window.viewport_size();
        scene(
            self.started.elapsed().as_secs_f32(),
            f32::from(viewport.width),
            f32::from(viewport.height),
            self.look.0,
            &self.spots,
        )
    }
}
