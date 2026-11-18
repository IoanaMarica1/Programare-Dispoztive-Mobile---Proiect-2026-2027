package com.wanderly.ui.group;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.wanderly.R;
import com.wanderly.data.remote.ApiClient;
import com.wanderly.ui.common.Avatars;

import java.util.List;

/**
 * Small round avatars for group trips: the traveller's initial on their own colour (the same colour
 * on every screen, by position in the group), with their photo on top when they have one.
 */
public final class MemberAvatars {

    private MemberAvatars() {
    }

    /** The colour of the traveller at {@code index} (0 = organiser). */
    public static int color(Context context, int index) {
        TypedArray colors = context.getResources().obtainTypedArray(R.array.member_colors);
        try {
            return colors.getColor(Math.floorMod(index, colors.length()), 0);
        } finally {
            colors.recycle();
        }
    }

    public static View create(Context context, String name, String picture, int index, int sizeDp) {
        float density = context.getResources().getDisplayMetrics().density;
        int size = Math.round(sizeDp * density);

        FrameLayout frame = new FrameLayout(context);
        frame.setLayoutParams(new LinearLayout.LayoutParams(size, size));

        TextView initial = new TextView(context);
        initial.setLayoutParams(new FrameLayout.LayoutParams(size, size));
        initial.setBackgroundResource(R.drawable.bg_member_avatar);
        initial.setBackgroundTintList(ColorStateList.valueOf(color(context, index)));
        initial.setGravity(Gravity.CENTER);
        initial.setText(Avatars.initialOf(name));
        initial.setTextColor(context.getColor(R.color.white));
        initial.setTypeface(Typeface.DEFAULT_BOLD);
        initial.setTextSize(Math.max(10f, sizeDp * 0.42f));
        initial.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        frame.addView(initial);

        String url = ApiClient.absoluteUrl(picture);
        if (url != null) {
            ImageView photo = new ImageView(context);
            int ring = Math.round(2 * density);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size - 2 * ring, size - 2 * ring);
            lp.gravity = Gravity.CENTER;
            photo.setLayoutParams(lp);
            photo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            Glide.with(photo).load(url).circleCrop().into(photo);
            frame.addView(photo);
        }
        frame.setContentDescription(name);
        return frame;
    }

    /** One avatar in a list of people: who and where they sit in the group. */
    public static final class Person {
        final String name, picture;
        final int index;

        public Person(String name, String picture, int index) {
            this.name = name;
            this.picture = picture;
            this.index = index;
        }
    }

    /**
     * Fills {@code into} with overlapping avatars; past {@code max} people the last one becomes "+n".
     */
    public static void stack(LinearLayout into, List<Person> people, int sizeDp, int max) {
        into.removeAllViews();
        Context context = into.getContext();
        int overlap = -Math.round(sizeDp * 0.3f * context.getResources().getDisplayMetrics().density);
        int shown = people.size() > max ? max - 1 : people.size();
        for (int i = 0; i < shown; i++) {
            Person p = people.get(i);
            add(into, create(context, p.name, p.picture, p.index, sizeDp), i == 0 ? 0 : overlap);
        }
        if (shown < people.size()) {
            View more = create(context, "", null, 7, sizeDp);
            TextView text = (TextView) ((FrameLayout) more).getChildAt(0);
            text.setText("+" + (people.size() - shown));
            text.setBackgroundTintList(ColorStateList.valueOf(context.getColor(R.color.text_secondary)));
            add(into, more, overlap);
        }
    }

    private static void add(LinearLayout into, View avatar, int marginStart) {
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) avatar.getLayoutParams();
        lp.setMarginStart(marginStart);
        into.addView(avatar, lp);
    }
}
