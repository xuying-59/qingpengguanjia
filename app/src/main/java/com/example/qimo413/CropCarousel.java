package com.example.qimo413;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

/** Three bundled greenhouse photographs, advanced only while their page is visible. */
final class CropCarousel {
    private static final int[] PHOTOS = {R.drawable.carousel_tomatoes, R.drawable.carousel_strawberries, R.drawable.carousel_irrigation};
    private static final String[] CAPTIONS = {"温室里的西红柿", "温室草莓", "灌溉与节水"};
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ViewPager2 pager;
    private final TextView caption;
    private final TextView position;
    private final Runnable advance = new Runnable() {
        @Override public void run() {
            if (!pager.isAttachedToWindow() || !pager.isShown()) return;
            pager.setCurrentItem((pager.getCurrentItem() + 1) % PHOTOS.length, true);
            handler.postDelayed(this, 4000);
        }
    };
    private final ViewPager2.OnPageChangeCallback pageChange = new ViewPager2.OnPageChangeCallback() {
        @Override public void onPageSelected(int index) {
            caption.setText(CAPTIONS[index]);
            StringBuilder dots = new StringBuilder();
            for (int i = 0; i < PHOTOS.length; i++) {
                if (i > 0) dots.append(' ');
                dots.append(i == index ? '●' : '○');
            }
            position.setText(dots.toString());
        }
    };

    CropCarousel(View root) {
        pager = root.findViewById(R.id.crop_carousel);
        caption = root.findViewById(R.id.crop_carousel_caption);
        position = root.findViewById(R.id.crop_carousel_position);
        pager.setAdapter(new RecyclerView.Adapter<PhotoHolder>() {
            @NonNull @Override public PhotoHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                ImageView image = new ImageView(parent.getContext());
                image.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                return new PhotoHolder(image);
            }
            @Override public void onBindViewHolder(@NonNull PhotoHolder holder, int index) {
                holder.image.setImageResource(PHOTOS[index]);
                holder.image.setContentDescription(CAPTIONS[index]);
            }
            @Override public int getItemCount() { return PHOTOS.length; }
        });
        pager.registerOnPageChangeCallback(pageChange);
    }

    void start() { handler.removeCallbacks(advance); handler.postDelayed(advance, 4000); }
    void stop() { handler.removeCallbacks(advance); }
    void release() { stop(); pager.unregisterOnPageChangeCallback(pageChange); }

    private static final class PhotoHolder extends RecyclerView.ViewHolder {
        final ImageView image;
        PhotoHolder(ImageView image) { super(image); this.image = image; }
    }
}
