/*
 * Copyright 2011 Matthew Precious
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mattprecious.locnotifier;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.Projection;

public class RadiusOverlay extends Overlay {

    private GeoPoint geoPoint;
    private float meters;
    private PointType type;

    enum PointType {
        LOCATION, DESTINATION
    }

    public RadiusOverlay(GeoPoint geoPoint, float meters, PointType type) {
        this.geoPoint = geoPoint;
        this.meters = meters;
        this.type = type;
    }

    public float getMeters() {
        return meters;
    }

    @Override
    public void draw(Canvas canvas, MapView mapView, boolean shadow) {
        super.draw(canvas, mapView, shadow);

        Projection projection = mapView.getProjection();

        float radius = projection.metersToEquatorPixels(meters);

        Point point = new Point();
        projection.toPixels(geoPoint, point);

        Paint fill = new Paint();
        fill.setAntiAlias(true);
        fill.setStyle(Paint.Style.FILL);

        Paint stroke = new Paint();
        stroke.setAntiAlias(true);
        stroke.setStyle(Paint.Style.STROKE);

        if (type == PointType.LOCATION) {
            fill.setARGB(75, 180, 232, 94);
            stroke.setARGB(75, 11, 66, 57);
        } else {
            fill.setARGB(75, 189, 73, 50);
            stroke.setARGB(150, 189, 73, 50);
        }

        canvas.drawCircle(point.x, point.y, radius, fill);
        canvas.drawCircle(point.x, point.y, radius, stroke);
    }

}
