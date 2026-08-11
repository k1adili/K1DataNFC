package com.k1datanfc;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Full-screen image viewer with pinch-to-zoom and pan support.
 * Tap anywhere to close.
 */
public class FullScreenImageActivity extends AppCompatActivity {

    public static final String EXTRA_IMAGE_PATH = "image_path";

    private ImageView   imageView;
    private ProgressBar progressBar;

    // Touch / zoom state
    private Matrix      matrix     = new Matrix();
    private Matrix      savedMatrix = new Matrix();
    private static final int NONE  = 0, DRAG = 1, ZOOM = 2;
    private int         mode       = NONE;
    private PointF      start      = new PointF();
    private PointF      mid        = new PointF();
    private float       oldDist    = 1f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fullscreen_image);

        imageView   = findViewById(R.id.iv_fullscreen);
        progressBar = findViewById(R.id.progress_fullscreen);

        String path = getIntent().getStringExtra(EXTRA_IMAGE_PATH);
        if (path == null) { finish(); return; }

        // Hide system UI for immersive experience
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        loadImage(path);
        setupTouch();
    }

    private void loadImage(String path) {
        progressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            byte[] bytes = K1Application.getInstance().getDatabaseManager()
                    .loadDecryptedImage(path);
            if (bytes != null) {
                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    imageView.setImageBitmap(bmp);
                });
            } else {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "خطا در بارگذاری تصویر", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        }).start();
    }

    private void setupTouch() {
        imageView.setScaleType(ImageView.ScaleType.MATRIX);
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
                case MotionEvent.ACTION_POINTER_UP:
                    // Single tap to close (no drag / zoom happened)
                    if (mode == DRAG) {
                        float dx = Math.abs(event.getX() - start.x);
                        float dy = Math.abs(event.getY() - start.y);
                        if (dx < 8 && dy < 8) { finish(); return true; }
                    }
                    mode = NONE;
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (mode == DRAG) {
                        matrix.set(savedMatrix);
                        matrix.postTranslate(event.getX() - start.x,
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
        point.set((e.getX(0) + e.getX(1)) / 2, (e.getY(0) + e.getY(1)) / 2);
    }
}
