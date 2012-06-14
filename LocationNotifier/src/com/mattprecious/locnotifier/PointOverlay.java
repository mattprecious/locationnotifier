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
import com.mattprecious.locnotifier.RadiusOverlay.PointType;

public class PointOverlay extends Overlay {

    private GeoPoint geoPoint;
    private PointType type;

    public PointOverlay(GeoPoint geoPoint, PointType type) {
        this.geoPoint = geoPoint;
        this.type = type;
    }

    public GeoPoint getPoint() {
        return geoPoint;
    }

    @Override
    public void draw(Canvas canvas, MapView mapView, boolean shadow) {
        super.draw(canvas, mapView, shadow);

        Projection projection = mapView.getProjection();

        Point point = new Point();
        projection.toPixels(geoPoint, point);

        Paint fill = new Paint();
        fill.setAntiAlias(true);
        fill.setStyle(Paint.Style.FILL);

        Paint stroke = new Paint();
        stroke.setAntiAlias(true);
        stroke.setStyle(Paint.Style.STROKE);

        if (type == PointType.LOCATION) {
            fill.setARGB(255, 16, 91, 99);
            stroke.setARGB(255, 11, 66, 57);
        } else {
            fill.setARGB(255, 189, 73, 50);
            stroke.setARGB(255, 146, 20, 12);
        }

        canvas.drawCircle(point.x, point.y, 10, fill);
        canvas.drawCircle(point.x, point.y, 10, stroke);
    }

}
