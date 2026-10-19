import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class Compresser {
  private static final byte MARKER = (byte) 0xFF;
	public static void main (String[] args) throws Exception {
		// we need three arguments, file in, and file out, compress/decompress
    if (args.length != 3) {
      System.err.println("Usage: java Compresser <input> <output> <compress|decompress>");
      System.exit(1);
    }

		String fileInput = args[0];
		String fileOut = args[1];
		boolean compress = args[2].toLowerCase().equals("compress");
    
    long size = Files.size(Path.of(fileInput));

		try (InputStream in = Files.newInputStream(Path.of(fileInput));
         OutputStream out = Files.newOutputStream(Path.of(fileOut))) {
      if (compress) {
        compress(in, out, size);
      } else {
        decompress(in, out, size);
      }
    }

    System.out.println(compress ? "Compression finished." : "Decompression finished.");
	}

  private static void compress(InputStream in, OutputStream out, long size) throws Exception {
    byte[] buffer = new byte[4096];
    int bytesRead;
    long byteindex = 0;
    int zeroRun = 0;

    long totalBuffers = (size + 4095) / 4096;
    long countEvery = Math.max(1, totalBuffers / 10);
    
    byte currRunByte = 0;
    boolean inRun = false;
    System.out.print("Progress: [");
    while ((bytesRead = in.read(buffer)) != -1) {
      byteindex++;
      if ((byteindex % (countEvery)) == 0) {
        System.out.print("-");
      }
      for (int i = 0; i < bytesRead; i++) {
        
        byte b = buffer[i];

        if (!inRun) {
          currRunByte = b;
          zeroRun++;
          inRun = true;
        } else if (b == currRunByte) {
          zeroRun++;
          if (zeroRun == 255) {
            writeZeroRun(out, zeroRun, currRunByte);
            inRun = false;
            zeroRun = 0;
          }
        } else {
          writeZeroRun(out, zeroRun, currRunByte);
          currRunByte = b;
          zeroRun = 1;

        }
      }
    }
    // EOF zero flush
    if (inRun) {
      writeZeroRun(out, zeroRun, currRunByte);
    }
    System.out.println("]");
  }

  private static void writeZeroRun(OutputStream out, int count, byte curr) throws Exception {
    if (count >= 3) {
      // only compress if actually worth it, so length >= 3 of run
      out.write(MARKER);
      out.write(count); // length between 0x00-0xFF, which is why we cut off run in prev method
      out.write(curr);
    } else {
      // run too short so we dont compress
      for (int i = 0; i < count; i++) {
        if (curr == MARKER) {
          out.write(MARKER);
          out.write(0);
        } else {
          out.write(curr);
        }
      }
    }
  }

  private static void decompress(InputStream in, OutputStream out, long size) throws Exception {
    int b;
    
    long countEvery = Math.max(1, size / 10);
    long byteindex = 0;
    System.out.print("Progress: [");
    while ((b = in.read()) != -1) {
      byteindex++;
      if ((byteindex % (countEvery)) == 0) {
        System.out.print("-");
      }
      if (b == (MARKER & 0xFF)) { // found marker
        int length = in.read();

        if (length == 0) {
          // FF FF reads as FF because we escaped it
          out.write(MARKER);
        } else {
          // FF + length means write that many zeroes
          int value = in.read();
          for (int i = 0; i < length; i++) {
            out.write(value);
          }
        }
      } else {
        // write out uncompressed data as usual
        out.write(b);
      }
    }
    System.out.println("]");
  }
}
