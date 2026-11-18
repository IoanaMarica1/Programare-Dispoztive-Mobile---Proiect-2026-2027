package com.wanderly.ui.group;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;
import com.wanderly.R;
import com.wanderly.data.remote.ApiClient;
import com.wanderly.data.remote.dto.PreferencesResponse;
import com.wanderly.data.remote.dto.UserResponse;
import com.wanderly.ui.common.Dialogs;
import com.wanderly.ui.common.Session;
import com.wanderly.ui.preferences.PreferencesActivity;
import com.wanderly.ui.preferences.PreferencesWizard;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * D1: who is coming on a group trip. The organiser is always first, with their saved preferences;
 * friends are added in a bottom sheet, edited by tapping them and removed by swiping (with Undo).
 * The list goes back to the adventure screen, which plans the trip.
 */
public class TravellersActivity extends AppCompatActivity {

    public static final String EXTRA_TRAVELLERS = "travellers";

    private final List<Traveller> travellers = new ArrayList<>();
    private Adapter adapter;
    private View root, emptyState, swipeHint, addButton;
    private TextView countText;
    private MaterialButton doneButton;
    private MaterialCardView organiserCard;
    private String ownName, ownPicture;
    private Boolean ownPreferences;  // null until loaded

    public static Intent intent(Context context, List<Traveller> travellers) {
        return new Intent(context, TravellersActivity.class)
                .putExtra(EXTRA_TRAVELLERS, Traveller.toJson(travellers));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_travellers);

        travellers.addAll(Traveller.fromJson(savedInstanceState != null
                ? savedInstanceState.getString(EXTRA_TRAVELLERS)
                : getIntent().getStringExtra(EXTRA_TRAVELLERS)));

        root = findViewById(R.id.travellersRoot);
        emptyState = findViewById(R.id.travellersEmpty);
        swipeHint = findViewById(R.id.travellersSwipeHint);
        addButton = findViewById(R.id.addTravellerButton);
        countText = findViewById(R.id.travellersCount);
        doneButton = findViewById(R.id.travellersDone);
        organiserCard = findViewById(R.id.organiserCard);
        ((TextView) findViewById(R.id.travellersSubtitle))
                .setText(getString(R.string.travellers_subtitle, Traveller.MAX_PEOPLE));

        RecyclerView list = findViewById(R.id.travellersList);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter();
        list.setAdapter(adapter);
        new ItemTouchHelper(new SwipeToRemove()).attachToRecyclerView(list);

        addButton.setOnClickListener(v -> addTraveller());
        doneButton.setOnClickListener(v -> done());
        findViewById(R.id.travellersBack).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        // Back keeps the changes too: nothing typed here is thrown away.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                returnTravellers();
            }
        });

        bindOrganiser();
        refresh();
        loadProfile();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadOwnPreferences();  // they may have just come back from the wizard
    }

    // ------------------------------------------------------------------ organiser

    private void loadProfile() {
        ApiClient.getService(getApplicationContext()).getProfile().enqueue(new Callback<UserResponse>() {
            @Override
            public void onResponse(@NonNull Call<UserResponse> call, @NonNull Response<UserResponse> response) {
                if (isDestroyed()) return;
                if (response.code() == 401) {
                    Session.expire(TravellersActivity.this);
                } else if (response.body() != null) {
                    ownName = response.body().name;
                    ownPicture = response.body().profilePicture;
                    bindOrganiser();
                }
            }

            @Override
            public void onFailure(@NonNull Call<UserResponse> call, @NonNull Throwable t) {
                // "You" without a photo is fine; the preferences check reports the network problem.
            }
        });
    }

    private void loadOwnPreferences() {
        ApiClient.getService(getApplicationContext()).getPreferences().enqueue(new Callback<PreferencesResponse>() {
            @Override
            public void onResponse(@NonNull Call<PreferencesResponse> call,
                                   @NonNull Response<PreferencesResponse> response) {
                if (isDestroyed()) return;
                if (!response.isSuccessful()) return;
                // 204 (no body): the wizard was never completed, and the backend refuses a group trip.
                PreferencesResponse body = response.body();
                ownPreferences = body != null && body.interests != null && !body.interests.isEmpty();
                bindOrganiser();
            }

            @Override
            public void onFailure(@NonNull Call<PreferencesResponse> call, @NonNull Throwable t) {
                if (!isDestroyed()) Dialogs.showNetworkError(TravellersActivity.this, () -> loadOwnPreferences());
            }
        });
    }

    private void bindOrganiser() {
        String name = ownName != null ? ownName : getString(R.string.travellers_you);
        FrameLayout slot = organiserCard.findViewById(R.id.travellerAvatar);
        slot.removeAllViews();
        slot.addView(MemberAvatars.create(this, name, ownPicture, 0, 44));
        ((TextView) organiserCard.findViewById(R.id.travellerName)).setText(ownName != null
                ? getString(R.string.travellers_you) + " · " + ownName : name);
        organiserCard.findViewById(R.id.travellerRemove).setVisibility(View.GONE);

        TextView subtitle = organiserCard.findViewById(R.id.travellerSubtitle);
        boolean missing = Boolean.FALSE.equals(ownPreferences);
        subtitle.setText(missing ? R.string.travellers_you_missing : R.string.travellers_you_subtitle);
        subtitle.setTextColor(getColor(missing ? R.color.error : R.color.text_secondary));
        organiserCard.setStrokeColor(getColor(missing ? R.color.error : R.color.outline_soft));
        organiserCard.setClickable(missing);
        organiserCard.setOnClickListener(missing ? v -> showOwnPreferencesMissing() : null);
    }

    private void showOwnPreferencesMissing() {
        Dialogs.info(this, R.drawable.ic_person, getString(R.string.dialog_own_prefs_title),
                        getString(R.string.dialog_own_prefs_message))
                .setNegativeButton(R.string.action_not_now, null)
                .setPositiveButton(R.string.dialog_own_prefs_action,
                        (d, w) -> startActivity(new Intent(this, PreferencesActivity.class)))
                .show();
    }

    // ------------------------------------------------------------------ friends

    private void addTraveller() {
        if (travellers.size() + 1 >= Traveller.MAX_PEOPLE) {
            Dialogs.info(this, R.drawable.ic_group, getString(R.string.dialog_full_title),
                            getString(R.string.dialog_full_message, Traveller.MAX_PEOPLE))
                    .setPositiveButton(R.string.action_ok, null)
                    .show();
            return;
        }
        AddTravellerSheet.show(this, new ArrayList<>(travellers), null, t -> {
            travellers.add(t);
            adapter.notifyItemInserted(travellers.size() - 1);
            refresh();
        });
    }

    private void edit(int position) {
        Traveller current = travellers.get(position);
        if (!current.needsAnswers()) return;  // a Wanderly user's saved preferences are private
        List<Traveller> others = new ArrayList<>(travellers);
        others.remove(position);
        AddTravellerSheet.show(this, others, current, t -> {
            int index = travellers.indexOf(current);
            if (index < 0) return;
            travellers.set(index, t);
            adapter.notifyItemChanged(index);
        });
    }

    private void remove(int position) {
        if (position < 0 || position >= travellers.size()) return;
        Traveller removed = travellers.remove(position);
        adapter.notifyItemRemoved(position);
        refresh();
        Snackbar.make(root, getString(R.string.travellers_removed, removed.name), Snackbar.LENGTH_LONG)
                .setAnchorView(doneButton)
                .setAction(R.string.action_undo, v -> {
                    int at = Math.min(position, travellers.size());
                    travellers.add(at, removed);
                    adapter.notifyItemInserted(at);
                    refresh();
                })
                .show();
    }

    /** Counter, empty state, Add button and the Continue label follow the list. */
    private void refresh() {
        int people = travellers.size() + 1;
        countText.setText(getString(R.string.travellers_count, people, Traveller.MAX_PEOPLE));
        emptyState.setVisibility(travellers.isEmpty() ? View.VISIBLE : View.GONE);
        swipeHint.setVisibility(travellers.isEmpty() ? View.GONE : View.VISIBLE);
        addButton.setAlpha(people >= Traveller.MAX_PEOPLE ? 0.5f : 1f);
        doneButton.setText(travellers.isEmpty() ? getString(R.string.travellers_done)
                : getString(R.string.travellers_done_count, people));
    }

    // ------------------------------------------------------------------ leaving

    private void done() {
        if (travellers.isEmpty()) {
            Dialogs.info(this, R.drawable.ic_person_add, getString(R.string.dialog_no_friends_title),
                            getString(R.string.dialog_no_friends_message))
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton(R.string.travellers_add, (d, w) -> addTraveller())
                    .show();
            return;
        }
        if (Boolean.FALSE.equals(ownPreferences)) {
            showOwnPreferencesMissing();
            return;
        }
        returnTravellers();
    }

    private void returnTravellers() {
        setResult(RESULT_OK, new Intent().putExtra(EXTRA_TRAVELLERS, Traveller.toJson(travellers)));
        finish();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(EXTRA_TRAVELLERS, Traveller.toJson(travellers));
    }

    /** "History, Food" from the chosen interests, in the wizard's labels. */
    private String interestsText(List<String> interests) {
        String[] labels = getResources().getStringArray(PreferencesWizard.Step.INTERESTS.labels);
        List<String> values = PreferencesWizard.Step.INTERESTS.values;
        List<String> names = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            if (interests != null && interests.contains(values.get(i))) names.add(labels[i]);
        }
        return TextUtils.join(", ", names);
    }

    // ------------------------------------------------------------------ list

    private final class Adapter extends RecyclerView.Adapter<Adapter.Holder> {

        final class Holder extends RecyclerView.ViewHolder {
            final MaterialCardView card;
            final FrameLayout avatar;
            final TextView name, subtitle;
            final View removeButton;

            Holder(View view) {
                super(view);
                card = (MaterialCardView) view;
                avatar = view.findViewById(R.id.travellerAvatar);
                name = view.findViewById(R.id.travellerName);
                subtitle = view.findViewById(R.id.travellerSubtitle);
                removeButton = view.findViewById(R.id.travellerRemove);
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_traveller, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            Traveller t = travellers.get(position);
            h.avatar.removeAllViews();
            h.avatar.addView(MemberAvatars.create(TravellersActivity.this, t.name, t.profilePicture,
                    position + 1, 44));
            h.name.setText(t.name);
            if (t.onWanderly() && t.hasPreferences) {
                h.subtitle.setText(R.string.travellers_on_wanderly);
            } else if (t.onWanderly()) {
                h.subtitle.setText(getString(R.string.travellers_on_wanderly_manual, interestsText(t.interests)));
            } else {
                h.subtitle.setText(getString(R.string.travellers_by_hand, interestsText(t.interests)));
            }
            h.card.setClickable(t.needsAnswers());
            h.card.setOnClickListener(v -> edit(h.getBindingAdapterPosition()));
            h.removeButton.setContentDescription(getString(R.string.travellers_remove_description, t.name));
            h.removeButton.setOnClickListener(v -> remove(h.getBindingAdapterPosition()));
        }

        @Override
        public int getItemCount() {
            return travellers.size();
        }
    }

    /** Swipe a card either way to remove it; the card fades as it goes. */
    private final class SwipeToRemove extends ItemTouchHelper.SimpleCallback {

        SwipeToRemove() {
            super(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
        }

        @Override
        public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder a,
                              @NonNull RecyclerView.ViewHolder b) {
            return false;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direction) {
            remove(holder.getBindingAdapterPosition());
        }

        @Override
        public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder holder,
                                float dX, float dY, int actionState, boolean active) {
            float width = holder.itemView.getWidth();
            holder.itemView.setAlpha(width == 0 ? 1f : 1f - Math.min(1f, Math.abs(dX) / width));
            super.onChildDraw(c, rv, holder, dX, dY, actionState, active);
        }

        @Override
        public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder holder) {
            holder.itemView.setAlpha(1f);
            super.clearView(rv, holder);
        }
    }
}
