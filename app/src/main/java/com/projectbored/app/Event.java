package com.projectbored.app;

import android.location.Location;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Drifter on 20/11/17.
 */

public class Event {
    private String title;
    private String description;
    private long expiryTime;
    private String location;

    public Event(String title, String description, long expiryTime, Location location) {
        this.title = title;
        this.description = description;
        this.expiryTime = expiryTime;
        this.location = Double.toString(location.getLatitude()) + "," + Double.toString(location.getLongitude());
    }

    public String getLocationString() {
        return location;
    }

    public Map<String, Object> toMap() {
        HashMap<String, Object> result = new HashMap<>();
        result.put("Title", title);
        result.put("Description", description);
        result.put("ExpiryTime", expiryTime);
        result.put("Location", location);
        result.put("Interested", 1);

        return result;
    }
}
