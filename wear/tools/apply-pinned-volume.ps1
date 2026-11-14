$ErrorActionPreference='Stop'
$repo=Join-Path $env:USERPROFILE 'Documents\PTIV_Walkman_Android_20261113\ptiv-walkman-offline'
$file=Join-Path $repo 'wear\app\src\main\java\live\ptiv\watch\WatchActivity.java'
$source=[IO.File]::ReadAllText($file).Replace(([string][char]13+[char]10),[string][char]10)
function ReplaceRequired([string]$before,[string]$after){
    if(!$script:source.Contains($before)){throw "MISSING_ANCHOR: "+$before.Substring(0,[Math]::Min(60,$before.Length))}
    $script:source=$script:source.Replace($before,$after)
}
ReplaceRequired 'private TextView volumeLabel;' 'private TextView volumeLabel, pinnedVolumeLabel;'
ReplaceRequired @'
        scroller.addView(root);
        basePage=scroller;
        setContentView(basePage);
'@ @'
        scroller.addView(root);
        FrameLayout mainRoot=new FrameLayout(this);
        mainRoot.setBackgroundColor(Color.rgb(9,18,22));
        scroller.setClipToPadding(false);
        scroller.setPadding(0,0,0,dp(66));
        mainRoot.addView(scroller,new FrameLayout.LayoutParams(-1,-1));
        // Persistent one-tap controls, independent of scroll position.
        LinearLayout volumeDock=new LinearLayout(this);
        volumeDock.setOrientation(LinearLayout.HORIZONTAL);
        volumeDock.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable background =
            new android.graphics.drawable.GradientDrawable();
        background.setColor(Color.rgb(18,46,47));
        background.setCornerRadius(dp(22));
        background.setStroke(dp(1),Color.rgb(78,129,115));
        volumeDock.setBackground(background);
        Button dockMinus=button("-",v->changeMusicVolume(-1));
        Button dockPlus=button("+",v->changeMusicVolume(1));
        dockMinus.setTextSize(23); dockPlus.setTextSize(23);
        dockMinus.setContentDescription("Decrease music volume");
        dockPlus.setContentDescription("Increase music volume");
        pinnedVolumeLabel=label("VOL",12,LIME);
        volumeDock.addView(dockMinus,new LinearLayout.LayoutParams(dp(52),dp(47)));
        volumeDock.addView(pinnedVolumeLabel,new LinearLayout.LayoutParams(0,dp(47),1));
        volumeDock.addView(dockPlus,new LinearLayout.LayoutParams(dp(52),dp(47)));
        FrameLayout.LayoutParams dockPosition=new FrameLayout.LayoutParams(
            -1,dp(52),Gravity.BOTTOM);
        dockPosition.setMargins(dp(8),0,dp(8),dp(7));
        mainRoot.addView(volumeDock,dockPosition);
        basePage=mainRoot;
        setContentView(basePage);
'@
ReplaceRequired @'
            if(volumeSlider!=null){
'@ @'
            if(pinnedVolumeLabel!=null){
                pinnedVolumeLabel.setText(fixed?"OUTPUT":("VOL "+value+"/"+max));
                pinnedVolumeLabel.setContentDescription(fixed?
                    "Fixed media output":("Music volume "+value+" of "+max));
            }
            if(volumeSlider!=null){
'@
[IO.File]::WriteAllText($file,$script:source)
'PINNED_VOLUME_PATCH_OK'
git -C $repo diff --stat
