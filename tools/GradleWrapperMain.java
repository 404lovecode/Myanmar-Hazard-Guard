package org.gradle.wrapper;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/**
 * Lightweight offline-bundled wrapper bootstrap for this project.
 * It downloads the official Gradle distribution on the developer's machine.
 * This is not a substitute for the official Gradle wrapper when publishing.
 */
public final class GradleWrapperMain {
    public static void main(String[] args) throws Exception {
        File project=new File(System.getProperty("user.dir"));
        File propFile=new File(project,"gradle/wrapper/gradle-wrapper.properties");
        if(!propFile.isFile()) throw new IOException("Run the wrapper from the project root: "+project);
        Properties properties=new Properties();
        try(InputStream in=new FileInputStream(propFile)){properties.load(in);}
        String source=properties.getProperty("distributionUrl");
        if(source==null || !source.startsWith("https://services.gradle.org/distributions/gradle-"))
            throw new IOException("Unexpected Gradle distribution URL");
        String name=source.substring(source.lastIndexOf('/')+1).replace("-bin.zip","");
        if(!name.matches("gradle-[0-9][0-9.]*")) throw new IOException("Unexpected Gradle distribution name");
        File cache=new File(project,".gradle-bootstrap");
        cache.mkdirs();
        File gradleRoot=new File(cache,name);
        boolean win=System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        File binary=new File(gradleRoot,"bin/gradle"+(win?".bat":""));
        if(!binary.isFile()) {
            File zip=new File(cache,name+"-bin.zip");
            if(!zip.isFile() || zip.length()<10_000_000L) {
                System.out.println("Downloading official Gradle distribution once: "+source);
                URLConnection connection=new URL(source).openConnection();
                connection.setConnectTimeout(20_000);connection.setReadTimeout(120_000);
                try(InputStream in=connection.getInputStream(); OutputStream out=new FileOutputStream(zip)) {
                    byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
                }
            }
            System.out.println("Extracting Gradle to "+cache.getAbsolutePath());
            String cachePath=cache.getCanonicalPath()+File.separator;
            try(ZipInputStream in=new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)))) {
                ZipEntry entry;
                while((entry=in.getNextEntry())!=null) {
                    File dest=new File(cache,entry.getName());
                    if(!dest.getCanonicalPath().startsWith(cachePath))throw new IOException("Invalid zip entry");
                    if(entry.isDirectory())dest.mkdirs();
                    else {
                        dest.getParentFile().mkdirs();
                        try(OutputStream out=new FileOutputStream(dest)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
                    }
                    in.closeEntry();
                }
            }
            if(!binary.isFile())throw new IOException("Gradle executable not found after extraction");
        }
        if(!win)binary.setExecutable(true);
        ArrayList<String> command=new ArrayList<>();
        if(win){command.add("cmd.exe");command.add("/c");}
        command.add(binary.getAbsolutePath());Collections.addAll(command,args);
        Process process=new ProcessBuilder(command).directory(project).inheritIO().start();
        System.exit(process.waitFor());
    }
}
