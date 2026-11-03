package mm.hazardguard;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Safe in-app article browser. Opens translated article through Google Translate
 * web proxy in our own WebView; never launches the Google Translate application.
 * Some sites block this, so the original page is always one tap away.
 */
public final class NewsReaderActivity extends Activity {
    private WebView web;
    private TextView status;
    private String originalUrl, translatedUrl;
    private boolean translationMode;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        originalUrl=getIntent().getStringExtra("url");
        String language=NewsTranslation.target(getIntent().getStringExtra("language"));
        if(!WorldNewsFeed.safeNewsUrl(originalUrl)){finish();return;}
        translatedUrl=NewsTranslation.websiteUrl(originalUrl,language);
        String title=getIntent().getStringExtra("title");
        String translation=getIntent().getStringExtra("translation");
        boolean burmese="my".equals(language);

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(248,247,241));
        root.setPadding(0,0,0,0);
        setContentView(root);

        LinearLayout top=new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(12),dp(12),dp(12),dp(9));
        top.setBackgroundColor(Color.rgb(23,62,64));
        TextView back=control("‹  "+(burmese?"နောက်သို့":"Back"),true);
        back.setOnClickListener(v->finish());top.addView(back);
        TextView translated=control(burmese?"ဘာသာပြန်":"Translate",true);
        translated.setOnClickListener(v->show(true));top.addView(translated);
        TextView source=control(burmese?"မူရင်း":"Original",true);
        source.setOnClickListener(v->show(false));top.addView(source);
        root.addView(top);

        if(title!=null && !title.isEmpty()) {
            TextView header=new TextView(this);
            String head=(translation!=null&&!translation.isEmpty())?translation:title;
            header.setText(head);
            header.setTextColor(Color.rgb(27,58,54));
            header.setTextSize(16);header.setPadding(dp(16),dp(12),dp(16),dp(8));
            root.addView(header);
        }
        status=new TextView(this);
        status.setTextColor(Color.rgb(97,123,118));status.setTextSize(12);
        status.setPadding(dp(16),dp(3),dp(16),dp(6));root.addView(status);

        web=new WebView(this);
        WebSettings settings=web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String scheme=request.getUrl()==null?"":request.getUrl().getScheme();
                return !"https".equalsIgnoreCase(scheme);
            }
            @Override public void onPageFinished(WebView view,String url) {
                status.setText(translationMode?
                    "Translated webpage when supported • Some publishers restrict translation":
                    "Original publisher article • Headlines may differ from translations");
            }
            @Override public void onReceivedError(WebView view,int code,String description,String failingUrl) {
                status.setText("Page unavailable. Try the Original tab or check your connection.");
            }
        });
        root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        show(true);
    }
    private TextView control(String text,boolean white) {
        TextView t=new TextView(this);
        t.setText(text);t.setTextColor(white?Color.WHITE:Color.BLACK);
        t.setTextSize(12);t.setPadding(dp(9),dp(10),dp(9),dp(10));
        t.setGravity(Gravity.CENTER);return t;
    }
    private int dp(int d){return Math.round(d*getResources().getDisplayMetrics().density);}
    private void show(boolean translate) {
        if(web==null)return;
        translationMode=translate && translatedUrl!=null && !translatedUrl.isEmpty();
        status.setText(translationMode?"Loading translated article inside Hazard Guard…":
            "Loading original publisher article inside Hazard Guard…");
        web.loadUrl(translationMode?translatedUrl:originalUrl);
    }
    @Override public void onBackPressed() {
        if(web!=null&&web.canGoBack())web.goBack();
        else super.onBackPressed();
    }
    @Override protected void onDestroy() {
        if(web!=null){web.stopLoading();web.destroy();web=null;}
        super.onDestroy();
    }
}
