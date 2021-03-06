package com.projectbored.app;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import io.fabric.sdk.android.Fabric;

import java.util.ArrayList;
import java.util.Calendar;

/**
 * Startup Code (works in following flow):
 * 1. Checks for app maintenance and updates
 * 2. Removes expired events
 * 3. Checks if onboarding has been completed (if false, user --> onboarding --> Login)
 * 4. Checks if logged in
 * 5. Opens MapsActivityCurrentPlace if logged in
 */

public class Startup extends AppCompatActivity {
    private static final String PREFS_NAME = "UserDetails";

    private boolean loggedIn;

    private DatabaseReference mDataRef;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Fabric.with(this, new Crashlytics());
        setContentView(R.layout.activity_startup);

        mDataRef = FirebaseDatabase.getInstance().getReference();
        mAuth = FirebaseAuth.getInstance();

        getUserData();

    }

    // Checks app for updates or maintenance
    private void checkAppStatus() {
        mDataRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                boolean underMaintenance = false;
                if(dataSnapshot.child("maintenance").exists()) {
                    underMaintenance = dataSnapshot.child("maintenance").getValue(boolean.class);
                }
                if(underMaintenance) {
                    Toast.makeText(Startup.this, "See GO is currently under maintenance.", Toast.LENGTH_SHORT).show();
                    Startup.this.finish();
                } else {
                    checkUpdates(dataSnapshot);
                    mDataRef.removeEventListener(this);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    // Check app for updates
    // 1: force update
    // else: choice to update
    private void checkUpdates(final DataSnapshot dataSnapshot) {

        final int versionCode = BuildConfig.VERSION_CODE;
        int latestVersionCode = dataSnapshot.child("VersionCode").getValue(Integer.class);
        if(versionCode < latestVersionCode) {
            AlertDialog.Builder updatePrompt = new AlertDialog.Builder(Startup.this);

            boolean critical = dataSnapshot.child("UpdateCritical").getValue(boolean.class);
            if(critical) {
                updatePrompt.setMessage("A new version of See GO is available.")
                        .setPositiveButton("Update", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                                        Uri.parse("https://play.google.com/store/apps/details?id=com.projectbored.app"));
                                startActivity(browserIntent);
                            }
                        })
                        .setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface dialogInterface) {
                                Startup.this.finish();
                            }
                        });
            } else {
                updatePrompt.setMessage("A new version of See GO is available. Update?")
                        .setPositiveButton("Update", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                                        Uri.parse("https://play.google.com/store/apps/details?id=com.projectbored.app"));
                                startActivity(browserIntent);
                            }
                        })
                        .setNegativeButton("Later", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                checkEvents(dataSnapshot);
                            }
                        });
            }
            updatePrompt.create().show();
        } else {
            checkEvents(dataSnapshot);
        }

    }

    // removes expired events (in Startup instead of MapsActivity due to certain errors)
    private void checkEvents(DataSnapshot dataSnapshot) {

        for(DataSnapshot ds : dataSnapshot.child("events").getChildren()) {
            long expiryTime = ds.child("ExpiryTime").getValue(Long.class);
            long timeNow = Calendar.getInstance().getTimeInMillis();

            if(timeNow >= expiryTime) {
                String key = ds.getKey();
                String location = ds.child("Location").getValue(String.class).replace('.', 'd');
                mDataRef.child("locations").child(location).child(key).removeValue();
                ds.getRef().removeValue();

                for(DataSnapshot dataSnapshot1 : dataSnapshot.child("users").getChildren()) {
                    for(DataSnapshot dataSnapshot2 : dataSnapshot1.child("EventsInterested").getChildren()) {
                        if(key.equals(dataSnapshot2.getKey())) {
                            dataSnapshot2.getRef().removeValue();
                        }
                    }

                    for(DataSnapshot dataSnapshot2 : dataSnapshot1.child("EventsNotInterested").getChildren()) {
                        if(key.equals(dataSnapshot2.getKey())) {
                            dataSnapshot2.getRef().removeValue();
                        }
                    }
                }
            }


        }

        /*int storiesWithoutURI = 0;
        ArrayList<String> keyArray = new ArrayList<>();
        for(DataSnapshot ds : dataSnapshot.child("stories").getChildren()) {
            if(!ds.hasChild("URI")) {
                storiesWithoutURI++;
                keyArray.add(ds.getKey());
            }
        }

        StringBuilder messageBuilder = new StringBuilder();
        messageBuilder.append("Number of stories without URI: ").append(storiesWithoutURI).append("\n").append("\n");
        for(String key : keyArray) {
            messageBuilder.append(key).append("\n");
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(Startup.this);
        builder.setMessage(messageBuilder.toString());
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                startApp();
            }
        });
        builder.show();*/

        startApp();
    }

    // Checks log-in and onboarding using SharedPreferences
    private void getUserData() {
        SharedPreferences preferences =  getSharedPreferences(PREFS_NAME, 0);

        // Check if onboarding has been completed & user Alert Builder
        if(!preferences.getBoolean("onboarding_complete",false)) {

            // Start the onboarding Activity (OLD AND WORKING)

            Intent onboarding = new Intent(this, Onboarding.class);
            startActivity(onboarding);

            // Close the Startup activity
            finish();



            // TO-DO: replace onboarding with alert. that appears after log in at maps activity?
            /*
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Hello, you seem to be a new user!");
            builder.setMessage("Check out our basic how-to for best experience");
            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    Intent login = new Intent(Startup.this, Login.class);
                    startActivity(login);

                    // start up URL

                    finish();
                }
            });
            builder.show();
            */

        } else {

            // If onboarding has been completed, check for logged in
            SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
            loggedIn = settings.getBoolean("Logged in", false);
            if(!loggedIn){
                Intent login = new Intent(this, Login.class);
                startActivity(login);

                finish();

            } else {
                String email = settings.getString("Email", "");
                String password = settings.getString("Password", "");
                //Toast.makeText(Startup.this, "Logged in as " + username + "." , Toast.LENGTH_LONG).show();
                verifyAccount(email, password);
            }

        }
    }

    // verify log-in
    private void verifyAccount(final String email, String password) {
        if(!getSharedPreferences(PREFS_NAME, 0).getBoolean("old_account_option_seen", false))
        {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Hello!");
            builder.setMessage("If you have created an account using an earlier version of the app, you may now migrate the data over from your old account. This option can be found in the menu on your profile page.");
            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dialogInterface.dismiss();
                }
            });
            builder.show();

            SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, 0).edit();
            editor.putBoolean("old_account_option_seen", true);
            editor.apply();
        }


        if (email.equals("") || password.equals("")) {
            Intent logout = new Intent(Startup.this, Logout.class);
            logout.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(logout);

            finish();

        } else {
            mAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                @Override
                public void onComplete(@NonNull Task<AuthResult> task) {
                    if(task.isSuccessful()) {
                        Toast.makeText(Startup.this, "Logged in as " + email + ".", Toast.LENGTH_SHORT).show();

                        checkAppStatus();
                    }
                }
            }).addOnFailureListener(Startup.this, new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Toast.makeText(Startup.this, "Account settings have changed. Please log in again.", Toast.LENGTH_SHORT).show();

                    Intent logout = new Intent(Startup.this, LogoutNoToast.class);
                    logout.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(logout);

                    finish();

                }
            });


        /* Old code
        /
        if(dataSnapshot.child(username).exists()){
            if(dataSnapshot.child(username).child("Password").getValue(String.class).equals(password)) {
                Toast.makeText(Startup.this, "Logged in as " + username + "." , Toast.LENGTH_SHORT).show();

                Intent start = new Intent(Startup.this, MapsActivityCurrentPlace.class);
                start.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(start);
            } else {
                Toast.makeText(Startup.this, "Account settings have changed. Please log in again.", Toast.LENGTH_SHORT).show();

                Intent logout = new Intent(Startup.this, Logout.class);
                logout.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(logout);
            }
        } else {
            Toast.makeText(Startup.this, "Account settings have changed. Please log in again.", Toast.LENGTH_SHORT).show();

            Intent logout = new Intent(Startup.this, Logout.class);
            logout.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(logout);

            finish();
        }*/
        }
    }

    private void startApp(){
        Intent i = new Intent(Startup.this, MapsActivityCurrentPlace.class);
        startActivity(i);
        finish();
    }
}
