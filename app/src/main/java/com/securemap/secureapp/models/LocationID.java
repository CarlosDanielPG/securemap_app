package com.securemap.secureapp.models;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class LocationID {

    @SerializedName("ID")
    @Expose
    private String ID;
    @SerializedName("Latitude")
    @Expose
    private Double Latitude;
    @SerializedName("Longitude")
    @Expose
    private Double Longitude;

    public String getID() {
        return ID;
    }

    public void setID(String ID) {
        this.ID = ID;
    }

    public Double getLatitude() {
        return Latitude;
    }

    public void setLatitude(Double latitude) {
        Latitude = latitude;
    }

    public Double getLongitude() {
        return Longitude;
    }

    public void setLongitude(Double longitude) {
        Longitude = longitude;
    }

    @Override
    public String toString() {
        return "ID: " + this.ID + ", Latitude: " + this.Latitude + ", Longitude: " + this.Longitude;
    }
}
