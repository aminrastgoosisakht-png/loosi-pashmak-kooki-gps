package com.den.loosi

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity(), LocationListener {
    private lateinit var web: WebView
    private lateinit var locationManager: LocationManager
    private val requestCode = 41
    private var tracking = false
    private var pageReady = false
    private var latestLocation: Location? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                pageReady = true
                latestLocation?.let { showLocation(it) }
            }
        }
        setContentView(web)
        web.loadDataWithBaseURL("https://localhost/", page, "text/html", "UTF-8", null)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        if (hasLocationPermission()) {
            startLocation()
        } else {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                requestCode
            )
        }
    }

    private fun hasLocationPermission(): Boolean {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun startLocation() {
        if (tracking || !hasLocationPermission()) return
        try {
            var activeProvider = false
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER
            )
            for (provider in providers) {
                if (locationManager.isProviderEnabled(provider)) {
                    locationManager.requestLocationUpdates(provider, 3000L, 1f, this)
                    activeProvider = true
                    locationManager.getLastKnownLocation(provider)?.let { candidate ->
                        val previous = latestLocation
                        if (previous == null || candidate.time > previous.time ||
                            (candidate.accuracy < previous.accuracy && candidate.time >= previous.time - 30000L)
                        ) {
                            latestLocation = candidate
                            if (pageReady) showLocation(candidate)
                        }
                    }
                }
            }
            tracking = activeProvider
            if (!activeProvider) {
                setStatus("مکان‌یاب گوشی خاموش است؛ GPS یا Location را روشن کنید")
            } else if (latestLocation == null) {
                setStatus("در حال دریافت موقعیت دقیق گوشی…")
            }
        } catch (_: SecurityException) {
            setStatus("دسترسی مکان‌یابی داده نشده است")
        }
    }

    private fun showLocation(location: Location) {
        latestLocation = location
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(location.time))
        val js = "setPhoneLocation(${location.latitude},${location.longitude},${location.accuracy},'$time')"
        runOnUiThread {
            if (::web.isInitialized && pageReady) web.evaluateJavascript(js, null)
        }
    }

    private fun setStatus(message: String) {
        runOnUiThread {
            if (::web.isInitialized && pageReady) {
                val safe = message.replace("\\", "\\\\").replace("'", "\\'")
                web.evaluateJavascript("setStatus('$safe')", null)
            }
        }
    }

    override fun onLocationChanged(location: Location) {
        showLocation(location)
    }

    @Deprecated("Deprecated in Android API")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onProviderEnabled(provider: String) {
        if (hasLocationPermission()) startLocation()
    }

    override fun onProviderDisabled(provider: String) {
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
            !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        ) {
            setStatus("مکان‌یاب گوشی خاموش است")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == this.requestCode && hasLocationPermission()) {
            startLocation()
        } else if (requestCode == this.requestCode) {
            Toast.makeText(this, "برای نمایش موقعیت گوشی باید اجازه مکان‌یابی بدهید", Toast.LENGTH_LONG).show()
            setStatus("برای استفاده از نقشه، اجازه مکان‌یابی را فعال کنید")
        }
    }

    override fun onResume() {
        super.onResume()
        if (::locationManager.isInitialized && hasLocationPermission()) startLocation()
    }

    override fun onPause() {
        stopLocation()
        super.onPause()
    }

    private fun stopLocation() {
        if (tracking && ::locationManager.isInitialized) {
            locationManager.removeUpdates(this)
            tracking = false
        }
    }

    override fun onDestroy() {
        stopLocation()
        if (::web.isInitialized) web.destroy()
        super.onDestroy()
    }

    private val page = """
        <!doctype html>
        <html lang="fa" dir="rtl">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
          <title>لوسی، پشمک و کوکی</title>
          <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css">
          <style>
            *{box-sizing:border-box}html,body,#map{height:100%;width:100%;margin:0;font-family:sans-serif}
            .panel{position:absolute;z-index:1000;top:12px;right:12px;left:12px;background:#07532d;color:white;padding:13px 15px;border:1px solid #d7b64e;border-radius:17px;box-shadow:0 3px 14px #0004}
            .title{font-size:17px;font-weight:bold;display:flex;align-items:center;gap:9px}.logo{width:42px;height:42px;border:2px solid #e4c35b;border-radius:50%;background:#073b24;display:grid;place-items:center;font-size:21px}
            .status{display:block;margin-top:8px;color:#eff8ef;font-size:12px;line-height:1.7}.meta{display:block;color:#c6dfcd;font-size:10px;margin-top:3px}
            .locate{position:absolute;z-index:1000;bottom:24px;right:16px;left:16px;border:0;border-radius:13px;background:#d8b45a;color:#183526;padding:14px;font-size:15px;font-weight:bold;box-shadow:0 2px 8px #3335}
            .bird-pin{display:flex;align-items:center;gap:5px;white-space:nowrap;transform:translateY(-4px)}
            .bird-dot{width:30px;height:30px;border:2px solid #e9c75c;border-radius:50%;background:#07532d;display:grid;place-items:center;font-size:17px;box-shadow:0 0 0 3px #fff9,0 2px 7px #0006;position:relative}
            .bird-dot:after{content:'';position:absolute;top:-3px;left:-3px;width:7px;height:7px;border-radius:50%;background:#ff3030;box-shadow:0 0 7px #f00;animation:blink 1s infinite}
            @keyframes blink{0%,45%{opacity:1}46%,100%{opacity:.12}}
            .bird-name{background:#07532d;color:#fff;border:1px solid #e3c15a;border-radius:8px;padding:4px 7px;font-size:11px;font-weight:bold;box-shadow:0 2px 5px #0004}
            .leaflet-control-attribution{font-size:8px}
          </style>
        </head>
        <body>
          <div id="map"></div>
          <div class="panel">
            <div class="title"><div class="logo">🐤🐦</div><span>لوسی، پشمک و کوکی</span></div>
            <span class="status" id="status">در انتظار دریافت موقعیت گوشی…</span>
            <span class="meta" id="meta">فعلاً موقعیت گوشی به‌عنوان موقعیت هر سه پرنده نمایش داده می‌شود؛ ردیاب سخت‌افزاری هنوز متصل نیست.</span>
          </div>
          <button class="locate" onclick="centerOnPhone()">◎ نمایش موقعیت فعلی</button>
          <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
          <script>
            const map=L.map('map',{zoomControl:false}).setView([32.5,53],5);
            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap'}).addTo(map);
            L.control.zoom({position:'bottomright'}).addTo(map);
            let birdMarkers=[],circle=null,lastPoint=null;
            const birds=[{name:'لوسی',emoji:'🐤',dx:-9},{name:'پشمک',emoji:'🐦',dx:0},{name:'کوکی',emoji:'🐤',dx:9}];
            function setStatus(message){document.getElementById('status').textContent=message;}
            function setPhoneLocation(lat,lon,accuracy,time){
              lastPoint=[lat,lon];
              birdMarkers.forEach(m=>map.removeLayer(m));birdMarkers=[];
              if(circle)map.removeLayer(circle);
              birds.forEach(b=>{
                const lngOffset=b.dx*0.00001/Math.max(.2,Math.cos(lat*Math.PI/180));
                const icon=L.divIcon({className:'',html:'<div class="bird-pin"><span class="bird-dot">'+b.emoji+'</span><span class="bird-name">'+b.name+'</span></div>',iconSize:[100,38],iconAnchor:[50,19]});
                const m=L.marker([lat,lon+lngOffset],{icon}).addTo(map).bindPopup(b.name+' — موقعیت فعلی گوشی');
                birdMarkers.push(m);
              });
              circle=L.circle(lastPoint,{radius:accuracy,color:'#16854c',fillOpacity:.10,weight:2}).addTo(map);
              map.setView(lastPoint,16);
              setStatus('موقعیت گوشی دریافت شد و فعلاً برای هر سه پرنده ثبت می‌شود | دقت: '+Math.round(accuracy)+' متر');
              document.getElementById('meta').textContent='آخرین به‌روزرسانی: '+time+' — موقعیت هر سه نشانگر یکسان و متعلق به گوشی است، نه ردیاب روی پرنده.';
            }
            function centerOnPhone(){
              if(lastPoint){map.setView(lastPoint,17);if(birdMarkers.length)birdMarkers[0].openPopup();}
              else setStatus('هنوز موقعیتی دریافت نشده؛ GPS گوشی را روشن کنید.');
            }
          </script>
        </body>
        </html>
    """.trimIndent()
}
