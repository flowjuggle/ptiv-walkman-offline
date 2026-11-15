package live.ptiv.watch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * Eleven Pearl taps -> opt-in provider hub.
 *
 * Spotify and YouTube Music retain their own authentication, DRM, offline
 * download entitlements, playback and update mechanisms. This app stores only
 * display names and HTTPS URLs chosen by the user, never passwords or tokens.
 */
final class ServicesMode {
    private static final String PREFS="ptiv_custom_services_v1";
    private static final String KEY="custom_https_services";
    private static final int MAX_CUSTOM=12;
    private final Activity host;
    ServicesMode(Activity host){this.host=host;}

    private SharedPreferences prefs(){return host.getSharedPreferences(PREFS,Activity.MODE_PRIVATE);}
    private List<String> entries(){
        ArrayList<String> list=new ArrayList<>(prefs().getStringSet(KEY,Collections.emptySet()));
        Collections.sort(list,String.CASE_INSENSITIVE_ORDER);
        return list;
    }
    void show(){
        final String[] choices={
            "OPEN SPOTIFY",
            "OPEN YOUTUBE MUSIC",
            "MY SERVICES ("+entries().size()+")",
            "ADD A SERVICE",
            "ABOUT PROVIDER PLAYBACK"
        };
        new AlertDialog.Builder(host)
            .setTitle("PTIV • SERVICES")
            .setMessage("Eleven taps unlocked your personal services hub. Choose any installed music app, or add a supported HTTPS service shortcut. This doesn't change the PTIV offline library.")
            .setItems(choices,(d,i)->{
                if(i==0)launch("https://open.spotify.com/","Spotify");
                if(i==1)launch("https://music.youtube.com/","YouTube Music");
                if(i==2)listSaved();
                if(i==3)addService();
                if(i==4)info("Provider music remains in the provider's own official app or website. Offline downloads, logins and subscriptions are managed by Spotify, YouTube Music, or the chosen provider. This menu cannot import DRM-protected tracks into the PTIV offline player.");
            })
            .setNegativeButton("CLOSE",null)
            .show();
    }
    private void launch(String link,String title){
        Uri uri=validated(link);
        if(uri==null){info("Service link rejected. Only HTTPS URLs are accepted.");return;}
        try {
            Intent intent=new Intent(Intent.ACTION_VIEW,uri);
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            host.startActivity(intent);
        } catch(ActivityNotFoundException | SecurityException e){
            info(title+" isn't available on this watch. Install the provider's official Wear OS app, or use its HTTPS link from your paired phone.");
        }
    }
    private Uri validated(String link){
        if(link==null||link.length()>700)return null;
        try{
            Uri uri=Uri.parse(link.trim());
            String host=uri.getHost();
            if(!"https".equalsIgnoreCase(uri.getScheme())
                ||host==null||host.length()>253||host.contains(" ")
                ||uri.getUserInfo()!=null||uri.getPort()!=-1)return null;
            return uri;
        }catch(Exception e){return null;}
    }
    private void addService(){
        if(entries().size()>=MAX_CUSTOM){info("Maximum of "+MAX_CUSTOM+" custom services reached. Remove one first.");return;}
        LinearLayout form=new LinearLayout(host);
        form.setOrientation(LinearLayout.VERTICAL);
        EditText name=new EditText(host);name.setSingleLine(true);name.setHint("Service name");
        EditText url=new EditText(host);url.setSingleLine(true);
        url.setHint("https://your-music-service.example");
        form.addView(name);
        form.addView(url);
        new AlertDialog.Builder(host)
            .setTitle("ADD MUSIC SERVICE")
            .setMessage("Save a shortcut only. Never enter passwords, tokens or account secrets here.")
            .setView(form)
            .setPositiveButton("SAVE",(d,i)->{
                String n=name.getText().toString().trim();
                String u=url.getText().toString().trim();
                if(n.isEmpty()||n.length()>38||n.indexOf('\t')>=0||n.indexOf('\n')>=0){
                    info("Enter a service name of 1–38 characters.");return;
                }
                if(validated(u)==null){info("Enter a valid HTTPS service URL without a password or port.");return;}
                Set<String> entries=new HashSet<>(prefs().getStringSet(KEY,Collections.emptySet()));
                if(entries.size()>=MAX_CUSTOM){info("Custom service limit reached.");return;}
                entries.add(n+"\t"+u);
                prefs().edit().putStringSet(KEY,entries).apply();
                info(n+" added. Your credentials are never stored by PTIV.");
            })
            .setNegativeButton("CANCEL",null)
            .show();
    }
    private void listSaved(){
        List<String> saved=entries();
        if(saved.isEmpty()){info("No custom services. Use ADD A SERVICE to save an HTTPS shortcut.");return;}
        String[] options=new String[saved.size()+1];
        for(int i=0;i<saved.size();i++){
            String[] row=saved.get(i).split("\t",2);
            options[i]=row[0];
        }
        options[saved.size()]="REMOVE A SERVICE";
        new AlertDialog.Builder(host).setTitle("MY SERVICES")
            .setItems(options,(d,index)->{
                if(index==saved.size()){removeService();return;}
                String[] row=saved.get(index).split("\t",2);
                if(row.length==2)launch(row[1],row[0]);
            })
            .setNegativeButton("BACK",null).show();
    }
    private void removeService(){
        List<String> all=entries();
        String[] names=new String[all.size()];
        for(int i=0;i<all.size();i++)names[i]=all.get(i).split("\t",2)[0];
        new AlertDialog.Builder(host)
            .setTitle("REMOVE SHORTCUT")
            .setItems(names,(d,i)->new AlertDialog.Builder(host)
                .setTitle("Remove "+names[i]+"?")
                .setMessage("Removes only the PTIV shortcut, not the provider's account or music.")
                .setPositiveButton("REMOVE",(confirm,which)->{
                    Set<String> list=new HashSet<>(prefs().getStringSet(KEY,Collections.emptySet()));
                    list.remove(all.get(i));
                    prefs().edit().putStringSet(KEY,list).apply();
                })
                .setNegativeButton("CANCEL",null).show())
            .setNegativeButton("BACK",null).show();
    }
    private void info(String message){
        new AlertDialog.Builder(host).setTitle("PTIV • SERVICES")
            .setMessage(message).setPositiveButton("OK",null).show();
    }
}
