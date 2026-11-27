from pathlib import Path
p=Path('app/src/main/java/com/phantom/p2cx/MainActivity.java')
s=p.read_text(encoding='utf-8')
def rep(a,b):
    global s
    assert s.count(a)==1,a
    s=s.replace(a,b,1)
rep('import android.view.Gravity;', '''import android.view.Gravity;
import android.view.View;
import android.graphics.Typeface;''')
rep('  scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1, -1));','''  scroll.setFillViewport(true);
  scroll.setClipToPadding(false);
  scroll.setVerticalScrollBarEnabled(false);
  content.setPadding(0,Ui.dp(this,8),0,Ui.dp(this,20));
  scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
  LinearLayout header=Ui.row(this);
  header.setPadding(0,Ui.dp(this,10),0,Ui.dp(this,18));
  content.addView(header);''')
rep('''  LinearLayout.LayoutParams iconLayout = new LinearLayout.LayoutParams(Ui.dp(this,100), Ui.dp(this,100));
  iconLayout.gravity=Gravity.CENTER; iconLayout.topMargin=Ui.dp(this,28); content.addView(icon,iconLayout);''','''  LinearLayout.LayoutParams iconLayout = new LinearLayout.LayoutParams(Ui.dp(this,58), Ui.dp(this,58));
  header.addView(icon,iconLayout);
  Ui.space(header,12);
  LinearLayout heading=new LinearLayout(this);
  heading.setOrientation(LinearLayout.VERTICAL);
  header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));''')
rep('  TextView title = Ui.text(this, "P2C_X", 26); title.setGravity(Gravity.CENTER); content.addView(title);','''  TextView title = Ui.text(this, "P2C_X", 24);
  title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
  heading.addView(title);''')
rep('subtitle.setGravity(Gravity.CENTER); subtitle.setTextColor(Ui.MUTED); content.addView(subtitle);','''subtitle.setTextColor(Ui.MUTED); heading.addView(subtitle);
  LinearLayout account=section(content,"บัญชีและสิทธิ์");
  LinearLayout controls=new LinearLayout(this);''')
# Group existing account widgets without altering callbacks or visibility.
rep('accessStatus.setPadding(0,Ui.dp(this,20),0,Ui.dp(this,8)); content.addView(accessStatus);','''accessStatus.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
  accessStatus.setPadding(Ui.dp(this,12),Ui.dp(this,10),Ui.dp(this,12),Ui.dp(this,10));
  accessStatus.setBackground(Ui.background(this,Ui.SECONDARY,false)); item(account,accessStatus);''')
rep('content.addView(nicknameInput);','''nicknameInput.setTextSize(14);
  nicknameInput.setMinHeight(Ui.dp(this,48));
  nicknameInput.setHintTextColor(Ui.MUTED);
  nicknameInput.setPadding(Ui.dp(this,12),Ui.dp(this,8),Ui.dp(this,12),Ui.dp(this,8));
  nicknameInput.setBackground(Ui.background(this,Ui.SURFACE,false));
  item(account,nicknameInput);''')
rep('content.addView(register);','item(account,register);')
rep('content.addView(retry);','item(account,retry);')
rep('privacy.setTextColor(Ui.MUTED); content.addView(privacy);','privacy.setTextColor(Ui.MUTED); privacy.setLineSpacing(Ui.dp(this,3),1f);')
rep('content.addView(code);','item(account,code); item(account,privacy);')
rep('  renderAccess();\n  Switch enabled', '''  renderAccess();
  controls=section(content,"การตั้งค่า");
  Switch enabled''')
rep('enabled.setPadding(0,Ui.dp(this,24),0,Ui.dp(this,20));','enabled.setPadding(0,Ui.dp(this,10),0,Ui.dp(this,10)); enabled.setMinHeight(Ui.dp(this,48));')
rep('content.addView(enabled);','item(controls,enabled);')
rep('boot.setPadding(0,Ui.dp(this,12),0,Ui.dp(this,24));','boot.setPadding(0,Ui.dp(this,10),0,Ui.dp(this,10)); boot.setMinHeight(Ui.dp(this,48));')
rep('content.addView(boot);','item(controls,boot);')
rep('content.addView(gallery);','item(controls,gallery);')
rep('status.setPadding(0,Ui.dp(this,20),0,Ui.dp(this,12)); content.addView(status);','''status.setPadding(Ui.dp(this,12),Ui.dp(this,10),Ui.dp(this,12),Ui.dp(this,10));
  status.setBackground(Ui.background(this,Ui.SECONDARY,false));
  LinearLayout.LayoutParams statusParams=new LinearLayout.LayoutParams(-1,-2);
  statusParams.topMargin=Ui.dp(this,8);
  controls.addView(status,1,statusParams);''')
rep('help.setTextColor(Ui.MUTED); content.addView(help);','''help.setTextColor(Ui.MUTED); help.setLineSpacing(Ui.dp(this,3),1f);
  LinearLayout about=section(content,"ข้อมูลแอปและอัปเดต");
  item(about,help);''')
rep('content.addView(updates);','item(about,updates);')
rep(' private void renderAccess() {',''' private LinearLayout section(LinearLayout parent,String label) {
  LinearLayout panel=new LinearLayout(this);
  panel.setOrientation(LinearLayout.VERTICAL);
  panel.setPadding(Ui.dp(this,16),Ui.dp(this,14),Ui.dp(this,16),Ui.dp(this,16));
  panel.setBackground(Ui.background(this,Ui.SURFACE,false));
  LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
  params.bottomMargin=Ui.dp(this,14);
  parent.addView(panel,params);
  TextView labelView=Ui.text(this,label,16);
  labelView.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
  panel.addView(labelView,new LinearLayout.LayoutParams(-1,-2));
  return panel;
 }
 private void item(LinearLayout parent,View view) {
  LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
  params.topMargin=Ui.dp(this,8);
  parent.addView(view,params);
 }
 private void renderAccess() {''')
# The temporary controls declaration does not need to allocate an unused view.
s=s.replace('  LinearLayout controls=new LinearLayout(this);\n','').replace('  controls=section(content,','  LinearLayout controls=section(content,')
p.write_text(s,encoding='utf-8')
