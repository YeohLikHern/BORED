package com.projectbored.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

public class Logout extends AppCompatActivity {
    final public static String PREFS_NAME = "UserDetails";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_logout);

        logout();
    }

    private void logout() {
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.remove("Username");
        editor.remove("Password");
        editor.putBoolean("Logged in", false);

        editor.apply();

        Toast.makeText(this, "Logged out.", Toast.LENGTH_SHORT).show();

        Intent login = new Intent(this, Login.class);
        startActivity(login);

        finish();
    }
}
