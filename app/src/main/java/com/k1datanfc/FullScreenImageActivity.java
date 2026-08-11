package com.k1datanfc;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class FullScreenImageActivity extends AppCompatActivity {

    public static final String EXTRA_IMAGE_PATH = "image_path";

    private ImageView   imageView;
    private ProgressBar progressBar;
    private Bitmap      loadedBitmap;

    // Touch state
    private final Matrix  matrix      = new Matrix();
    private final Matrix  savedMatrix = new Matrix();
    private static final int NONE = 0, DRAG = 1, ZOOM = 2;
    private int           mode   = NONE;
    private final PointF  start  = new PointF();
    private final PointF  mid    = new PointF();
    private float         oldDist = 1f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fullscreen_image);

        imageView   = findViewById(R.id.iv_fullscreen);
        progressBar = findViewById(R.id.progress_fullscreen);

        // Immersive fullscreen
        imageView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        String path = getIntent().getStringExtra(EXTRA_IMAGE_PATH);
        if (path == null) { finish(); return; }

        setupTouch();
        loadImage(path);
    }

    private void loadImage(String path) {
        progressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            byte[] bytes = K1Application.getInstance()
                    .getDatabaseManager().loadDecryptedImage(path);
            if (bytes == null) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "خطا در بارگذاری تصویر", Toast.LENGTH_SHORT).show();
                    finish();
                });
                return;
            }
            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                loadedBitmap = bmp;
                imageView.setScaleType(ImageView.ScaleType.MATRIX);
                imageView.setImageBitmap(bmp);

                // Wait for layout to know the view size, then fit the image
                if (imageView.getWidth() > 0) {
                    fitImageToView(bmp);
                } else {
                    imageView.getViewTreeObserver().addOnGlobalLayoutListener(
                            new ViewTreeObserver.OnGlobalLayoutListener() {
                                @Override public void onGlobalLayout() {
                                    imageView.getViewTreeObserver()
                                            .removeOnGlobalLayoutListener(this);
                                    fitImageToView(bmp);
                                }
                            });
                }
            });
        }).start();
    }

    /**
     * Scale and centre the bitmap so it fills the screen width (or height)
     * without cropping — exactly like fitCenter, but stored in our matrix
     * so pinch-zoom works from that starting position.
     */
    private void fitImageToView(Bitmap bmp) {
        float vw = imageView.getWidth();
        float vh = imageView.getHeight();
        float iw = bmp.getWidth();
        float ih = bmp.getHeight();

        float scale = Math.min(vw / iw, vh / ih);
        float dx = (vw - iw * scale) / 2f;
        float dy = (vh - ih * scale) / 2f;

        matrix.reset();
        matrix.postScale(scale, scale);
        matrix.postTranslate(dx, dy);
        imageView.setImageMatrix(matrix);
    }

    private void setupTouch() {
        imageView.setOnTouchListener((v, event) -> {
            switch (event.getAction() & MotionEvent.ACTION_MASK) {

                case MotionEvent.ACTION_DOWN:
                    savedMatrix.set(matrix);
                    start.set(event.getX(), event.getY());
                    mode = DRAG;
                    break;

                case MotionEvent.ACTION_POINTER_DOWN:
                    oldDist = spacing(event);
                    if (oldDist > 10f) {
                        savedMatrix.set(matrix);
                        midPoint(mid, event);
                        mode = ZOOM;
                    }
                    break;

                case MotionEvent.ACTION_UP:
                    if (mode == DRAG) {
                        float dx = Math.abs(event.getX() - start.x);
                        float dy = Math.abs(event.getY() - start.y);
                        if (dx < 12 && dy < 12) {
                            // Single tap — close
                            finish();
                            return true;
                        }
                    }
                    mode = NONE;
                    break;

                case MotionEvent.ACTION_POINTER_UP:
                    mode = NONE;
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (mode == DRAG) {
                        matrix.set(savedMatrix);
                        matrix.postTranslate(
                                event.getX() - start.x,
                                event.getY() - start.y);
                    } else if (mode == ZOOM) {
                        float newDist = spacing(event);
                        if (newDist > 10f) {
                            matrix.set(savedMatrix);
                            float scale = newDist / oldDist;
                            matrix.postScale(scale, scale, mid.x, mid.y);
                        }
                    }
                    break;
            }
            imageView.setImageMatrix(matrix);
            return true;
        });
    }

    private float spacing(MotionEvent e) {
        float dx = e.getX(0) - e.getX(1);
        float dy = e.getY(0) - e.getY(1);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private void midPoint(PointF point, MotionEvent e) {
        point.set((e.getX(0) + e.getX(1)) / 2f, (e.getY(0) + e.getY(1)) / 2f);
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}
