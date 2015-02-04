package F3DImageProcessing_JOCL_;

import static java.lang.System.out;
import ij.IJ;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.jar.JarEntry;

public class InitializeJOCL
{
    /* statically initialize JOCL environment */
    public static boolean JOCL_Initialized = false;

    private static File createTempDirectory(String tmpdir)
	//public static File createTempDirectory(String tmpdir) Hari version
                throws IOException
    {
        final File temp;
        
        temp = File.createTempFile("jocl", Long.toString(System.nanoTime()), new File(tmpdir));

        if(!(temp.delete()))
        {
            throw new IOException("Could not delete temp file: " + temp.getAbsolutePath());
        }

        if(!(temp.mkdir()))
        {
            throw new IOException("Could not create temp directory: " + temp.getAbsolutePath());
        }

        return (temp);
    }

    private static void extractJarFile(String tmpdir, java.io.InputStream jarstream, String tmpname)
    {
        try 
        {
            java.io.File fx = new java.io.File(tmpdir.toString() + java.io.File.separator + tmpname + ".jar");
            java.io.FileOutputStream fxos = new java.io.FileOutputStream(fx);

            while (jarstream.available() > 0) 
                fxos.write(jarstream.read());
            fxos.close();
                
            @SuppressWarnings("resource")
			java.util.jar.JarFile jar = new java.util.jar.JarFile(fx.toString());
            Enumeration<JarEntry> e = jar.entries();

            while (e.hasMoreElements()) 
            {
                java.util.jar.JarEntry file = (java.util.jar.JarEntry) e.nextElement();
                java.io.File f = new java.io.File(tmpdir.toString() + java.io.File.separator + file.getName());
                if (file.isDirectory()) 
                {
                    f.mkdir();
                    continue;
                }

                java.io.InputStream is = jar.getInputStream(file); // get the input stream
                java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
                while (is.available() > 0) // write contents of 'is' to 'fos'
                    fos.write(is.read());
                fos.close();
                is.close();
            }
        }
        catch(IOException ex)
        {
            out.println("Failed to write");
        }
    }

    public static void InitJOCL() 
    {
        //com.jogamp.common.jvm.JNILibLoaderBase.disableLoading();
        //com.jogamp.gluegen.runtime.NativeLibLoader.disableLoading();

        /// load JOCL kernel..
        String tmpdir = IJ.getDirectory("temp");
        String jarsdir = IJ.getDirectory("imagej") + File.separator + "jars";
        String osname = System.getProperty("os.name");
        String arch = System.getProperty("sun.arch.data.model");

        String jocl_native_jar = "";
        String gluegen_native_jar = "";

        out.println(jarsdir);
        File folder = new File(jarsdir);
        File[] listOfFiles = folder.listFiles(); 

        String joclstr = "jocl-2.1.2-natives";
        String gluegenstr = "gluegen-rt-2.1.2-natives";
        String joclext = "";
        String gluegenext = "";
            
        if(osname.equals("Linux"))
        {
                joclstr += "-linux" + (arch.equals("64") ? "-amd64" : "-i586");
                gluegenstr += "-linux" + (arch.equals("64") ? "-amd64" : "-i586");
                joclext = ".so";
                gluegenext = ".so";
        }
        else if(osname.equals("Mac OS X"))
        {
            joclstr += "-macosx-universal";
            gluegenstr += "-macosx-universal";
            joclext = ".dylib";
            gluegenext = ".jnilib";
        }
        else
        {
                joclstr += "-windows" + (arch.equals("64") ? "-amd64" : "-i586");
                gluegenstr += "-windows" + (arch.equals("64") ? "-amd64" : "-i586");
                joclext = ".dll";
                gluegenext = ".dll";
        }

        out.println(osname + " " + arch + " " + joclstr + " " + gluegenstr);

        for (int i = 0; i < listOfFiles.length; i++) 
        {
            if (listOfFiles[i].isFile()) 
            {
                    String file = listOfFiles[i].getName();
                    if (file.startsWith(joclstr))
                        jocl_native_jar = file;
                    if (file.startsWith(gluegenstr))
                        gluegen_native_jar = file;
            }
        }

        out.println(System.getProperty("java.library.path"));
        out.println(tmpdir + " " + jarsdir + " " + osname + " " + jocl_native_jar + " " + gluegen_native_jar);

        String jdir = tmpdir;

        try {
            File fd = createTempDirectory(tmpdir);
            fd.deleteOnExit();
            jdir = fd.toString();
        }
        catch(Exception e){}

        out.println("natives/" + joclstr + ".jar");
        out.println("natives/" + gluegenstr + ".jar");

        System.loadLibrary(gluegenstr + ".jar");
        System.loadLibrary(joclstr + ".jar");

        java.io.InputStream joclstream = F3DImageProcessing_JOCL_.class.getResourceAsStream("/natives/" + joclstr + ".jar");
        java.io.InputStream gluegenstream = F3DImageProcessing_JOCL_.class.getResourceAsStream("/natives/" + gluegenstr + ".jar");
        
        extractJarFile(jdir, joclstream, "jocl");
        extractJarFile(jdir, gluegenstream, "gluegen");

        out.println(jdir + java.io.File.separator + "libgluegen-rt" + gluegenext);
        System.load(jdir + java.io.File.separator + "libgluegen-rt" + gluegenext);

        out.println(jdir + java.io.File.separator + "libjocl" + joclext);
        System.load(jdir + java.io.File.separator + "libjocl" + joclext);

        JOCL_Initialized = true;
    }
    
}
