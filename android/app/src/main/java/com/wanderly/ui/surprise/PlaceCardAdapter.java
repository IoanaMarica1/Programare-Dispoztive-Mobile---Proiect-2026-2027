package com.wanderly.ui.surprise;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.wanderly.R;
import com.wanderly.data.remote.dto.SurprisePlaceResponse;
import com.wanderly.ui.common.Avatars;
import com.wanderly.ui.common.Formats;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The swipeable cards of Surprise Me: one page per suggested place. */
final class PlaceCardAdapter extends RecyclerView.Adapter<PlaceCardAdapter.Holder> {

    interface Listener {
        void onTakeMeThere(SurprisePlaceResponse place);

        void onCheckIn(SurprisePlaceResponse place);
    }

    /** Payload for "only the buttons changed", so a check-in does not reload the photos. */
    private static final Object BUTTONS = new Object();

    private final Listener listener;
    private final List<SurprisePlaceResponse> places = new ArrayList<>();
    private final Set<String> checkedIn = new HashSet<>();
    private boolean working;
    private String checkingInId;

    PlaceCardAdapter(Listener listener) {
        this.listener = listener;
    }

    void submit(List<SurprisePlaceResponse> newPlaces, Collection<String> alreadyCheckedIn) {
        places.clear();
        places.addAll(newPlaces);
        checkedIn.clear();
        checkedIn.addAll(alreadyCheckedIn);
        working = false;
        checkingInId = null;
        notifyDataSetChanged();
    }

    /** While something is going on no button can be tapped twice; {@code checkingInId} says which card shows "Checking in…". */
    void setWorking(boolean working, String checkingInId) {
        this.working = working;
        this.checkingInId = working ? checkingInId : null;
        notifyItemRangeChanged(0, places.size(), BUTTONS);
    }

    void markCheckedIn(String id) {
        checkedIn.add(id);
        notifyItemRangeChanged(0, places.size(), BUTTONS);
    }

    SurprisePlaceResponse get(int position) {
        return places.get(position);
    }

    @Override
    public int getItemCount() {
        return places.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_surprise_place, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(places.get(position), position);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.contains(BUTTONS)) {
            holder.bindButtons(places.get(position));
        } else {
            onBindViewHolder(holder, position);
        }
    }

    final class Holder extends RecyclerView.ViewHolder {
        private final ImageView photo;
        private final TextView initial, counter, category, name, why, distance, open, address;
        private final View openRow, addressRow;
        private final MaterialButton takeMeThere, checkIn;

        Holder(@NonNull View view) {
            super(view);
            photo = view.findViewById(R.id.placePhoto);
            initial = view.findViewById(R.id.placeInitial);
            counter = view.findViewById(R.id.placeCounter);
            category = view.findViewById(R.id.placeCategory);
            name = view.findViewById(R.id.placeName);
            why = view.findViewById(R.id.placeWhy);
            distance = view.findViewById(R.id.placeDistance);
            open = view.findViewById(R.id.placeOpen);
            openRow = view.findViewById(R.id.placeOpenRow);
            address = view.findViewById(R.id.placeAddress);
            addressRow = view.findViewById(R.id.placeAddressRow);
            takeMeThere = view.findViewById(R.id.takeMeThereButton);
            checkIn = view.findViewById(R.id.checkInButton);

            // The place behind this card changes when the page is recycled, so look it up at tap time.
            takeMeThere.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) listener.onTakeMeThere(places.get(position));
            });
            checkIn.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) listener.onCheckIn(places.get(position));
            });
        }

        void bind(SurprisePlaceResponse p, int position) {
            android.content.Context context = itemView.getContext();
            counter.setText(context.getString(R.string.surprise_page_counter, position + 1, places.size()));
            counter.setVisibility(places.size() > 1 ? View.VISIBLE : View.GONE);
            category.setText(p.category);
            name.setText(p.name);
            why.setText(p.why);
            distance.setText(context.getString(R.string.surprise_distance_walk,
                    Formats.distance(p.distanceMeters), p.walkingMinutes));
            open.setText(p.openNote);
            openRow.setVisibility(p.openNote == null || p.openNote.isEmpty() ? View.GONE : View.VISIBLE);
            address.setText(p.address);
            addressRow.setVisibility(p.address == null || p.address.isEmpty() ? View.GONE : View.VISIBLE);

            // The initial is the placeholder: it shows while the photo loads and when there is none.
            initial.setText(Avatars.initialOf(p.name));
            photo.setContentDescription(context.getString(R.string.surprise_photo_description, p.name));
            if (p.photoUrl == null || p.photoUrl.isEmpty()) {
                Glide.with(photo).clear(photo);
                photo.setImageDrawable(null);
            } else {
                Glide.with(photo).load(p.photoUrl).centerCrop().into(photo);
            }
            bindButtons(p);
        }

        void bindButtons(SurprisePlaceResponse p) {
            boolean done = checkedIn.contains(p.id);
            takeMeThere.setEnabled(!working);
            checkIn.setEnabled(!working && !done);
            checkIn.setText(done ? R.string.surprise_checked_in
                    : working && p.id.equals(checkingInId) ? R.string.surprise_checking_in
                    : R.string.surprise_check_in);
        }
    }
}
