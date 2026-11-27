package com.phantom.p2cx;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;

/** Caller identity comes from Android; only our internal activities forward it. */
final class TargetProfile {
    static final String TARGET = "p2cx.target";
    static final String WIDTH = "p2cx.captureWidth", HEIGHT = "p2cx.captureHeight";

    static String target(Activity activity) {
        String caller = activity.getCallingPackage();
        if (activity.getPackageName().equals(caller)) {
            String forwarded = activity.getIntent().getStringExtra(TARGET);
            return forwarded == null || forwarded.isEmpty() ? "standalone" : forwarded;
        }
        return caller == null ? "standalone" : caller;
    }

    static Intent forward(Activity activity, Intent destination) {
        destination.putExtra(TARGET, target(activity));
        destination.putExtra(WIDTH, activity.getIntent().getIntExtra(WIDTH, 0));
        destination.putExtra(HEIGHT, activity.getIntent().getIntExtra(HEIGHT, 0));
        return destination;
    }

    static SharedPreferences gallery(Activity activity) {
        return activity.getSharedPreferences("gallery." + target(activity), 0);
    }
}
