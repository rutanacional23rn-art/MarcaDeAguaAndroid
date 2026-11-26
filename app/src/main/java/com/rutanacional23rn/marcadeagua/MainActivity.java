package com.rutanacional23rn.marcadeagua;

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final int PICK_PHOTOS = 10, PICK_LOGO = 11;
    private static final int PREVIEW_MAX = 1600, EXPORT_MAX = 4096, LOGO_MAX = 2000;

    // Estado
    private boolean vertical = true;
    private boolean inEditor = false;
    private final ArrayList<Uri> photos = new ArrayList<>();
    private int current = 0;
    private Bitmap previewBmp, logoBmp;
    private float logoX = .70f, logoY = .75f;   // esquina superior izquierda del logo, como fracción de la imagen
    private float logoFrac = .20f;               // ancho del logo como fracción del ancho de la imagen
    private float opacity = .75f;
    private float targetRatio = -1f;             // -1 = original
    private String outputFormat = "JPEG";

    // Vistas
    private PreviewView pv;
    private TextView status, counter;
    private SharedPreferences prefs;

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("watermark", MODE_PRIVATE);
        opacity = prefs.getInt("opacity", 75) / 100f;
        logoFrac = sizeFromProgress(prefs.getInt("size2", 30));
        logoX = prefs.getFloat("lx", .70f);
        logoY = prefs.getFloat("ly", .75f);
        String saved = prefs.getString("logoUri", null);
        if (saved != null) {
            try { logoBmp = decode(Uri.parse(saved), LOGO_MAX); } catch (Exception ignored) { logoBmp = null; }
        }
        showStart();
    }

    @Override
    public void onBackPressed() {
        if (inEditor) showStart(); else super.onBackPressed();
    }

    // ---------- utilidades de UI ----------
    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + .5f); }

    private TextView text(String s, int size) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(Color.rgb(35, 45, 55));
        return t;
    }

    private Button button(String s, Runnable r) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setOnClickListener(v -> r.run());
        return b;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private void applyInsets(LinearLayout root, int padDp) {
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(dp(padDp) + insets.getSystemWindowInsetLeft(),
                    dp(padDp) + insets.getSystemWindowInsetTop(),
                    dp(padDp) + insets.getSystemWindowInsetRight(),
                    dp(padDp) + insets.getSystemWindowInsetBottom());
            return insets;
        });
        root.setPadding(dp(padDp), dp(padDp), dp(padDp), dp(padDp));
        root.requestApplyInsets();
    }

    // ---------- Paso 1: orientación ----------
    private void showStart() {
        inEditor = false;
        LinearLayout root = column();
        root.setBackgroundColor(Color.WHITE);
        root.setGravity(Gravity.CENTER);
        applyInsets(root, 24);

        TextView title = text("MARCA DE AGUA", 24);
        title.setTypeface(null, 1);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView q = text("¿Qué fotos vas a subir?", 18);
        q.setGravity(Gravity.CENTER);
        q.setPadding(0, dp(24), 0, dp(6));
        root.addView(q);

        TextView hint = text("No se pueden mezclar verticales y horizontales en la misma tanda.", 13);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, 0, 0, dp(20));
        root.addView(hint);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(68));
        lp.setMargins(0, dp(6), 0, dp(6));
        root.addView(button("Fotos verticales", () -> { vertical = true; pickPhotos(); }), lp);
        root.addView(button("Fotos horizontales", () -> { vertical = false; pickPhotos(); }), lp);

        setContentView(root);
    }

    // ---------- Paso 2: editor ----------
    private void showEditor() {
        inEditor = true;
        targetRatio = -1f;
        LinearLayout root = column();
        root.setBackgroundColor(Color.WHITE);
        applyInsets(root, 10);

        LinearLayout top = new LinearLayout(this);
        top.addView(button("Cambiar fotos", this::showStart), new LinearLayout.LayoutParams(0, dp(48), 1));
        top.addView(button("Elegir logo", this::pickLogo), new LinearLayout.LayoutParams(0, dp(48), 1));
        root.addView(top);

        pv = new PreviewView(this);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, 0, 1);
        pp.setMargins(0, dp(6), 0, dp(4));
        root.addView(pv, pp);

        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.addView(button("◀", () -> go(-1)), new LinearLayout.LayoutParams(dp(64), dp(44)));
        counter = text("", 14);
        counter.setGravity(Gravity.CENTER);
        nav.addView(counter, new LinearLayout.LayoutParams(0, -2, 1));
        nav.addView(button("▶", () -> go(1)), new LinearLayout.LayoutParams(dp(64), dp(44)));
        root.addView(nav);

        ScrollView sc = new ScrollView(this);
        LinearLayout c = column();

        c.addView(text("Opacidad del logo", 14));
        SeekBar opBar = new SeekBar(this);
        opBar.setMax(100);
        opBar.setProgress(Math.round(opacity * 100));
        opBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) { opacity = p / 100f; pv.invalidate(); }
            public void onStartTrackingTouch(SeekBar s) { }
            public void onStopTrackingTouch(SeekBar s) { savePrefs(s.getProgress(), -1); }
        });
        c.addView(opBar);

        c.addView(text("Tamaño del logo", 14));
        SeekBar szBar = new SeekBar(this);
        szBar.setMax(100);
        szBar.setProgress(progressFromSize(logoFrac));
        szBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) { logoFrac = sizeFromProgress(p); pv.invalidate(); }
            public void onStartTrackingTouch(SeekBar s) { }
            public void onStopTrackingTouch(SeekBar s) { savePrefs(-1, s.getProgress()); }
        });
        c.addView(szBar);

        c.addView(text("Formato de archivo", 14));
        Spinner fmt = new Spinner(this);
        fmt.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"JPEG (más liviano)", "PNG (sin pérdida)"}));
        fmt.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> p) { }
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { outputFormat = pos == 0 ? "JPEG" : "PNG"; }
        });
        c.addView(fmt);

        final String[] labels;
        final float[] ratios;
        if (vertical) {
            labels = new String[]{"Original", "Vertical 4:5 (Facebook / Instagram)", "Stories / Reels 9:16", "Cuadrado 1:1"};
            ratios = new float[]{-1f, .8f, 9f / 16f, 1f};
        } else {
            labels = new String[]{"Original", "Horizontal 16:9 (Facebook)", "Horizontal 1.91:1 (Instagram)", "Cuadrado 1:1"};
            ratios = new float[]{-1f, 16f / 9f, 1.91f, 1f};
        }
        c.addView(text("Tamaño para redes sociales", 14));
        Spinner sz = new Spinner(this);
        sz.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        sz.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> p) { }
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { targetRatio = ratios[pos]; if (pv != null) pv.invalidate(); }
        });
        c.addView(sz);

        LinearLayout ex = new LinearLayout(this);
        ex.addView(button("Guardar esta foto", () -> export(false)), new LinearLayout.LayoutParams(0, dp(52), 1));
        ex.addView(button("Guardar todas", () -> export(true)), new LinearLayout.LayoutParams(0, dp(52), 1));
        c.addView(ex);

        status = text(logoBmp == null ? "Elige tu logo para empezar." : "Arrastra el logo para ubicarlo.", 13);
        c.addView(status);

        sc.addView(c);
        root.addView(sc, new LinearLayout.LayoutParams(-1, dp(230)));
        setContentView(root);
        loadCurrent();
    }

    private void go(int d) {
        if (photos.size() < 2) return;
        current = (current + d + photos.size()) % photos.size();
        loadCurrent();
    }

    private void loadCurrent() {
        Bitmap old = previewBmp;
        try {
            previewBmp = decode(photos.get(current), PREVIEW_MAX);
        } catch (Exception e) {
            previewBmp = null;
            status.setText("No se pudo abrir la foto: " + e.getMessage());
        }
        if (old != null) old.recycle();
        counter.setText("Foto " + (current + 1) + " de " + photos.size());
        pv.invalidate();
    }

    // ---------- selección de archivos ----------
    private void pickPhotos() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("image/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, PICK_PHOTOS);
    }

    private void pickLogo() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, PICK_LOGO);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null) return;
        try {
            if (req == PICK_LOGO) {
                Uri u = data.getData();
                if (u == null) return;
                try { getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) { }
                Bitmap nb = decode(u, LOGO_MAX);
                Bitmap old = logoBmp;
                logoBmp = nb;
                if (old != null) old.recycle();
                prefs.edit().putString("logoUri", u.toString()).apply();
                if (pv != null) pv.invalidate();
                if (status != null) status.setText("Logo cargado. Arrástralo sobre la foto.");
            } else if (req == PICK_PHOTOS) {
                ArrayList<Uri> picked = new ArrayList<>();
                ClipData cd = data.getClipData();
                if (cd != null) {
                    for (int n = 0; n < cd.getItemCount(); n++) picked.add(cd.getItemAt(n).getUri());
                } else if (data.getData() != null) {
                    picked.add(data.getData());
                }
                ArrayList<Uri> ok = new ArrayList<>();
                int rejected = 0;
                for (Uri u : picked) {
                    int[] d = dims(u);
                    boolean good = d[0] > 0 && d[1] > 0 && (vertical ? d[1] >= d[0] : d[0] >= d[1]);
                    if (good) ok.add(u); else rejected++;
                }
                if (ok.isEmpty()) {
                    Toast.makeText(this, vertical ? "Ninguna de las fotos es vertical." : "Ninguna de las fotos es horizontal.", Toast.LENGTH_LONG).show();
                    return;
                }
                photos.clear();
                photos.addAll(ok);
                current = 0;
                showEditor();
                String msg = photos.size() + " foto(s) lista(s)." + (logoBmp == null ? " Elige tu logo." : " Arrastra el logo para ubicarlo.");
                if (rejected > 0) msg += " Se omitieron " + rejected + " foto(s) que no son " + (vertical ? "verticales." : "horizontales.");
                status.setText(msg);
            }
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo abrir el archivo: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ---------- lectura de imágenes (respetando la orientación EXIF) ----------
    private int exifOrientation(Uri u) {
        try (InputStream in = getContentResolver().openInputStream(u)) {
            return new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (Exception e) {
            return ExifInterface.ORIENTATION_NORMAL;
        }
    }

    /** Ancho y alto tal como se ve la foto (ya con la rotación EXIF aplicada). */
    private int[] dims(Uri u) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(u)) { BitmapFactory.decodeStream(in, null, o); }
            int w = o.outWidth, h = o.outHeight;
            int ori = exifOrientation(u);
            if (ori >= 5 && ori <= 8) { int t = w; w = h; h = t; }
            return new int[]{w, h};
        } catch (Exception e) {
            return new int[]{0, 0};
        }
    }

    private Bitmap decode(Uri u, int maxSide) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(u)) { BitmapFactory.decodeStream(in, null, o); }
        int big = Math.max(o.outWidth, o.outHeight);
        int s = 1;
        while (big / (s * 2) >= maxSide) s *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = s;
        Bitmap b;
        try (InputStream in = getContentResolver().openInputStream(u)) { b = BitmapFactory.decodeStream(in, null, o2); }
        if (b == null) throw new IOException("imagen no válida");

        Matrix m = new Matrix();
        switch (exifOrientation(u)) {
            case 2: m.postScale(-1, 1); break;
            case 3: m.postRotate(180); break;
            case 4: m.postScale(1, -1); break;
            case 5: m.postRotate(90); m.postScale(-1, 1); break;
            case 6: m.postRotate(90); break;
            case 7: m.postRotate(-90); m.postScale(-1, 1); break;
            case 8: m.postRotate(-90); break;
            default: break;
        }
        if (!m.isIdentity()) {
            Bitmap r = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
            if (r != b) b.recycle();
            b = r;
        }
        int side = Math.max(b.getWidth(), b.getHeight());
        if (side > maxSide) {
            float f = (float) maxSide / side;
            Bitmap r = Bitmap.createScaledBitmap(b, Math.max(1, Math.round(b.getWidth() * f)), Math.max(1, Math.round(b.getHeight() * f)), true);
            if (r != b) b.recycle();
            b = r;
        }
        return b;
    }

    // ---------- exportación (misma geometría que la vista previa) ----------
    private void export(boolean batch) {
        if (photos.isEmpty()) { status.setText("Primero elige las fotos."); return; }
        if (logoBmp == null) { status.setText("Primero elige tu logo."); return; }
        status.setText("Guardando…");
        final ArrayList<Uri> list = new ArrayList<>();
        if (batch) list.addAll(photos); else list.add(photos.get(current));
        new Thread(() -> {
            int ok = 0;
            for (int n = 0; n < list.size(); n++) {
                try {
                    Bitmap src = decode(list.get(n), EXPORT_MAX);
                    Bitmap out = compose(src);
                    saveBitmap(out, "MarcaAgua_" + System.currentTimeMillis() + "_" + n);
                    if (out != src) out.recycle();
                    src.recycle();
                    ok++;
                } catch (Throwable ignored) { }
            }
            final int done = ok, total = list.size();
            runOnUiThread(() -> status.setText(done == total
                    ? "Listo: " + done + " archivo(s) guardado(s) en Imágenes/MarcaDeAgua. Los originales no se modificaron."
                    : "Se guardaron " + done + " de " + total + ". Algunas fotos fallaron."));
        }).start();
    }

    private Bitmap compose(Bitmap src) {
        int w = src.getWidth(), h = src.getHeight();
        int cw = w, ch = h;
        if (targetRatio > 0) {
            if ((float) w / h > targetRatio) { cw = Math.round(h * targetRatio); ch = h; }
            else { cw = w; ch = Math.round(w / targetRatio); }
        }
        int cx = (w - cw) / 2, cy = (h - ch) / 2;
        Bitmap out = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        c.drawBitmap(src, new Rect(cx, cy, cx + cw, cy + ch), new Rect(0, 0, cw, ch), p);
        float lw = cw * logoFrac;
        float lh = lw * logoBmp.getHeight() / logoBmp.getWidth();
        float lx = logoX * cw, ly = logoY * ch;
        p.setAlpha(Math.round(opacity * 255));
        c.drawBitmap(logoBmp, null, new RectF(lx, ly, lx + lw, ly + lh), p);
        return out;
    }

    private void saveBitmap(Bitmap b, String name) throws Exception {
        boolean png = outputFormat.equals("PNG");
        ContentValues v = new ContentValues();
        v.put(MediaStore.Images.Media.DISPLAY_NAME, name + (png ? ".png" : ".jpg"));
        v.put(MediaStore.Images.Media.MIME_TYPE, png ? "image/png" : "image/jpeg");
        v.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MarcaDeAgua");
        Uri u = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
        if (u == null) throw new IOException("no se pudo crear el archivo");
        try (OutputStream os = getContentResolver().openOutputStream(u)) {
            b.compress(png ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG, 95, os);
        }
    }

    // ---------- preferencias ----------
    private float sizeFromProgress(int p) { return .05f + (p / 100f) * .50f; }
    private int progressFromSize(float f) { return Math.max(0, Math.min(100, Math.round((f - .05f) / .50f * 100f))); }

    private void savePrefs(int opacityProgress, int sizeProgress) {
        SharedPreferences.Editor e = prefs.edit();
        if (opacityProgress >= 0) e.putInt("opacity", opacityProgress);
        if (sizeProgress >= 0) e.putInt("size2", sizeProgress);
        e.putFloat("lx", logoX).putFloat("ly", logoY).apply();
    }

    private void clampLogo(float imgAspect) {
        float lhF = logoBmp == null ? 0f : logoFrac * logoBmp.getHeight() / logoBmp.getWidth() * imgAspect;
        logoX = Math.max(0f, Math.min(1f - logoFrac, logoX));
        logoY = Math.max(0f, Math.min(1f - lhF, logoY));
    }

    // ---------- vista previa real: muestra el recorte final y el logo en su posición exacta ----------
    private class PreviewView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final RectF frame = new RectF();
        private final Rect srcRect = new Rect();
        private float grabDx, grabDy;

        PreviewView(Context c) {
            super(c);
            setBackgroundColor(Color.rgb(228, 232, 236));
        }

        private void layoutFrame() {
            int w = previewBmp.getWidth(), h = previewBmp.getHeight();
            int cw = w, ch = h;
            if (targetRatio > 0) {
                if ((float) w / h > targetRatio) { cw = Math.round(h * targetRatio); ch = h; }
                else { cw = w; ch = Math.round(w / targetRatio); }
            }
            int cx = (w - cw) / 2, cy = (h - ch) / 2;
            srcRect.set(cx, cy, cx + cw, cy + ch);
            float pad = dp(6);
            float availW = getWidth() - 2 * pad, availH = getHeight() - 2 * pad;
            float aspect = (float) cw / ch;
            float fw, fh;
            if (availW / availH > aspect) { fh = availH; fw = fh * aspect; } else { fw = availW; fh = fw / aspect; }
            float left = (getWidth() - fw) / 2f, top = (getHeight() - fh) / 2f;
            frame.set(left, top, left + fw, top + fh);
            clampLogo(aspect);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (previewBmp == null) return;
            layoutFrame();
            canvas.drawBitmap(previewBmp, srcRect, frame, paint);
            if (logoBmp != null) {
                float lw = frame.width() * logoFrac;
                float lh = lw * logoBmp.getHeight() / logoBmp.getWidth();
                float lx = frame.left + logoX * frame.width();
                float ly = frame.top + logoY * frame.height();
                paint.setAlpha(Math.round(opacity * 255));
                canvas.drawBitmap(logoBmp, null, new RectF(lx, ly, lx + lw, ly + lh), paint);
                paint.setAlpha(255);
            }
        }

        private void move(MotionEvent e) {
            float w = frame.width(), h = frame.height();
            if (w <= 0 || h <= 0) return;
            logoX = (e.getX() - grabDx - frame.left) / w;
            logoY = (e.getY() - grabDy - frame.top) / h;
            clampLogo(w / h);
            invalidate();
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (previewBmp == null || logoBmp == null || frame.width() <= 0) return true;
            float w = frame.width(), h = frame.height();
            float lw = w * logoFrac, lh = lw * logoBmp.getHeight() / logoBmp.getWidth();
            float lx = frame.left + logoX * w, ly = frame.top + logoY * h;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    float s = dp(12);
                    boolean inside = e.getX() >= lx - s && e.getX() <= lx + lw + s && e.getY() >= ly - s && e.getY() <= ly + lh + s;
                    if (inside) { grabDx = e.getX() - lx; grabDy = e.getY() - ly; }
                    else { grabDx = lw / 2f; grabDy = lh / 2f; }
                    move(e);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    move(e);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    savePrefs(-1, -1);
                    return true;
                default:
                    return true;
            }
        }
    }
}
