// Modified for Couchy HyperOS, 2026-11-23. Upstream and change notices: UPSTREAM.md, CHANGELOG.md.
package com.conreo.couchytv;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import org.json.JSONArray;
import org.json.JSONObject;

/** ADB shell-only shared-storage metadata and no-overwrite commit. Never runs as the app. */
public final class FileBridge {
    private static final File ROOT=new File("/sdcard");
    private static File checked(String path) throws Exception {
        if(!path.equals("/sdcard") && !path.startsWith("/sdcard/"))throw new SecurityException("只能访问电视内部共享存储");
        File f=new File(path).getCanonicalFile();String root=ROOT.getCanonicalPath();
        if(!f.getPath().equals(root) && !f.getPath().startsWith(root+"/"))throw new SecurityException("文件路径超出共享存储");
        return f;
    }
    private static String name(File f) throws Exception {
        String root=ROOT.getCanonicalPath();return "/sdcard"+f.getCanonicalPath().substring(root.length());
    }
    public static void main(String[] args) {
        JSONObject out=new JSONObject();
        try {
            int uid=android.os.Process.myUid();if(uid!=2000 && uid!=1000)throw new SecurityException("ADB only");
            if(args.length<2 || args.length>3)throw new IllegalArgumentException("参数无效");
            File f=checked(args[1]);
            if(args[0].equals("commit") && args.length==3) {
                File dst=checked(args[2]);
                if(!f.getName().matches("\\.couchy-transfer-[a-f0-9]{32}\\.part"))throw new SecurityException("不是临时传输文件");
                if(dst.exists())throw new IllegalArgumentException("同名文件已存在，原文件已保留");
                Files.move(f.toPath(),dst.toPath());out.put("path",name(dst));
            } else if(args[0].equals("check")) {
                out.put("path",name(f));out.put("exists",f.exists());out.put("directory",f.isDirectory());out.put("size",f.length());
            } else if(args[0].equals("list")) {
                if(!f.isDirectory())throw new IllegalArgumentException("目标不是文件夹");
                File[] files=f.listFiles();if(files==null)throw new IllegalArgumentException("无法读取这个文件夹");
                Arrays.sort(files,Comparator.comparing((File x)->!x.isDirectory()).thenComparing(File::getName,String.CASE_INSENSITIVE_ORDER));
                JSONArray entries=new JSONArray();
                for(File child:files) {
                    if(entries.length()>=1000)break;
                    if(child.getName().startsWith(".couchy-transfer-"))continue;
                    String root=ROOT.getCanonicalPath();String real=child.getCanonicalPath();
                    if(!real.equals(root) && !real.startsWith(root+"/"))continue;
                    entries.put(new JSONObject().put("name",child.getName()).put("path",name(child)).put("directory",child.isDirectory()).put("size",child.length()).put("modified",child.lastModified()));
                }
                out.put("path",name(f));out.put("entries",entries);out.put("truncated",files.length>1000);
            } else throw new IllegalArgumentException("操作无效");
        } catch(Exception e) {try {out.put("error",e.getMessage()==null?"文件操作失败":e.getMessage());}catch(Exception ignored){}}
        System.out.println(out.toString());System.exit(0);
    }
}
