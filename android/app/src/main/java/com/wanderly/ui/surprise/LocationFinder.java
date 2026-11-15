package com.wanderly.ui.surprise;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.location.LocationManagerCompat;
import androidx.core.os.CancellationSignal;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One fresh position of the phone, without Google Play services: asks the best provider the granted
 * permission allows, gives up after a few seconds, and then settles for a recent last-known position.
 */
final class LocationFinder {

    enum Failure {
        /** Location is switched off for the whole phone. */
        SERVICES_OFF,
        /** On, but no position arrived in time (indoors, no signal...). */
        UNAVAILABLE
    }

    interface Callback {
        void onLocation(@NonNull Location location);

        void onFailure(@NonNull Failure failure);
    }

    /** All providers together; after this the last-known position is the only thing left to try. */
    private static final long TOTAL_TIMEOUT_MS = 9000;
    /** How long one provider is given before the next is asked (GPS needs longer to get its first fix). */
    private static final long ATTEMPT_MS = 3000;
    private static final long GPS_ATTEMPT_MS = 5000;
    /** A last-known position older than this says too little about where the person is now. */
    private static final long MAX_AGE_MS = 2 * 60 * 1000;

    private LocationFinder() {
    }

    static boolean hasFinePermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Precise or approximate: either is enough to look for places nearby. */
    static boolean hasAnyPermission(Context context) {
        return hasFinePermission(context)
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Calls back exactly once, on the main thread. The returned signal cancels the search (the callback
     * is then not called); null when the answer was immediate.
     */
    @SuppressLint("MissingPermission")  // checked below: hasAnyPermission() / hasFinePermission()
    @Nullable
    static CancellationSignal find(Context context, boolean precise, Callback callback) {
        LocationManager manager = context.getSystemService(LocationManager.class);
        if (manager == null || !hasAnyPermission(context)) {
            callback.onFailure(Failure.UNAVAILABLE);
            return null;
        }
        if (!LocationManagerCompat.isLocationEnabled(manager)) {
            callback.onFailure(Failure.SERVICES_OFF);
            return null;
        }

        List<String> providers = usableProviders(manager, hasFinePermission(context), precise);
        if (providers.isEmpty()) {
            fallBack(manager, callback);
            return null;
        }

        Search search = new Search(manager, ContextCompat.getMainExecutor(context), providers, callback);
        CancellationSignal signal = new CancellationSignal();
        // Cancelling from outside stops whichever provider is being asked and silences the callback.
        signal.setOnCancelListener(search::cancel);
        search.tryProvider(0);
        return signal;
    }

    /**
     * Asks the providers one after the other, each for a limited time, so one that is on but silent (a fused
     * provider with no fix yet) doesn't stop a working GPS or network provider from being asked. Everything
     * runs on the main thread, so no locking is needed.
     */
    private static final class Search {
        private final LocationManager manager;
        private final Executor executor;
        private final List<String> providers;
        private final Callback callback;
        private final Handler main = new Handler(Looper.getMainLooper());
        private final long deadline = SystemClock.elapsedRealtime() + TOTAL_TIMEOUT_MS;

        private boolean finished;
        private CancellationSignal attempt;
        private Runnable attemptTimeout;

        Search(LocationManager manager, Executor executor, List<String> providers, Callback callback) {
            this.manager = manager;
            this.executor = executor;
            this.providers = providers;
            this.callback = callback;
        }

        @SuppressLint("MissingPermission")  // find() checked the permission before creating the search
        void tryProvider(int index) {
            if (finished) return;
            long left = deadline - SystemClock.elapsedRealtime();
            if (index >= providers.size() || left <= 0) {
                finished = true;
                fallBack(manager, callback);
                return;
            }

            String provider = providers.get(index);
            boolean last = index == providers.size() - 1;
            // The last provider gets whatever time is left; the others a share, GPS a longer one (cold start).
            long budget = last ? left
                    : Math.min(left, LocationManager.GPS_PROVIDER.equals(provider) ? GPS_ATTEMPT_MS : ATTEMPT_MS);

            AtomicBoolean answered = new AtomicBoolean(false);
            CancellationSignal current = new CancellationSignal();
            Runnable timeout = () -> {
                if (!answered.compareAndSet(false, true)) return;
                current.cancel();
                tryProvider(index + 1);
            };
            attempt = current;
            attemptTimeout = timeout;
            main.postDelayed(timeout, budget);

            LocationManagerCompat.getCurrentLocation(manager, provider, current, executor, location -> {
                if (!answered.compareAndSet(false, true)) return;
                main.removeCallbacks(timeout);
                if (finished) return;
                if (location != null) {
                    finished = true;
                    callback.onLocation(location);
                } else {
                    tryProvider(index + 1);   // this one had nothing: ask the next
                }
            });
        }

        void cancel() {
            finished = true;
            if (attemptTimeout != null) main.removeCallbacks(attemptTimeout);
            if (attempt != null) attempt.cancel();
        }
    }

    /**
     * Best first. Fused (Android 12+) is both quick and good. After it, network is quick but rough (fine
     * for "what is around me"), and GPS is exact but slow to start (needed for a 150 m check-in).
     */
    private static List<String> usableProviders(LocationManager manager, boolean fine, boolean precise) {
        List<String> providers = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && manager.isProviderEnabled(LocationManager.FUSED_PROVIDER)) {
            providers.add(LocationManager.FUSED_PROVIDER);
        }
        // GPS needs the precise permission.
        boolean gps = fine && manager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean network = manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        if (precise && gps) providers.add(LocationManager.GPS_PROVIDER);
        if (network) providers.add(LocationManager.NETWORK_PROVIDER);
        if (!precise && gps) providers.add(LocationManager.GPS_PROVIDER);
        return providers;
    }

    /** The freshest recent position any provider remembers, if there is one. */
    @SuppressLint("MissingPermission")  // only reached after the permission checks in find()
    private static void fallBack(LocationManager manager, Callback callback) {
        Location best = null;
        for (String provider : manager.getProviders(true)) {
            Location last;
            try {
                last = manager.getLastKnownLocation(provider);
            } catch (SecurityException | IllegalArgumentException e) {
                continue;
            }
            if (last != null && ageMillis(last) <= MAX_AGE_MS
                    && (best == null || last.getElapsedRealtimeNanos() > best.getElapsedRealtimeNanos())) {
                best = last;
            }
        }
        if (best != null) {
            callback.onLocation(best);
        } else {
            callback.onFailure(Failure.UNAVAILABLE);
        }
    }

    private static long ageMillis(Location location) {
        return (SystemClock.elapsedRealtimeNanos() - location.getElapsedRealtimeNanos()) / 1_000_000;
    }
}
