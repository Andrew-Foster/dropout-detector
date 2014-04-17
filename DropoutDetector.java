import java.io.File;
import javax.sound.sampled.*;
import java.util.*;
import java.lang.Math;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/*
* This tool takes in an audio file path as an argument and searches the file for 
* patterns indicating a digital error.  Results are printed to the console
*
* Example of use: java DropoutDetector myfile.wav
*
* TODO: Support stereo, (multiple filetypes - might already do this), tailor to sample rate.
* TODO: Decrease the number of false positives.
*/
public class DropoutDetector {

  // Main loads the requested file into a stream and sends it
  // to the stream checker.
  public static void main(String[] args)  {
    File soundFile;
    AudioInputStream ais;
    String filename = "";
    // Check that there is exactly 1 argument
    if (args.length == 1)
      filename = args[0];
    else
      usage();

    soundFile = new File(filename);
    
    if (!soundFile.canRead())
      readError();

    try {
      ais = AudioSystem.getAudioInputStream(soundFile);
      if (checkStreamFormat(ais)) {
        System.out.println(ais.getFormat().toString());
        checkStreamForDropouts(ais);
      }        
      ais.close();

    } catch (java.io.IOException e) {
      System.out.println("IO Exception reading bytes from file. " + e);
    } catch (javax.sound.sampled.UnsupportedAudioFileException e) {
      System.out.println("Unsupported Audio File. " + e);
    }
  }

  // Takes an audio input stream and prints
  // locations in the stream where suspected dropouts are.
  private static void checkStreamForDropouts(AudioInputStream ais) throws java.io.IOException {
    int numChannels = ais.getFormat().getChannels();
    
    // errorPositions will hold the positions in the file (which sample)
    // where an error was detected.
    ArrayList<Long> errorPositions = new ArrayList<Long>();

    if (ais.getFormat().getSampleSizeInBits() == 16) {
      //Run code for 16 bit audio
      byte[] readBytes = new byte[2]; // read 2 bytes (1 frame) at a time
      // for(int i = 0; i < 10; i++) {
      //   ais.read(readBytes); 
      //   int blah = bytesToInt16Bit(readBytes);
      //   System.out.println(Integer.toString(i+1) + ". Sample as Int: " + blah);
      // }

      ais.read(readBytes);
      int previousSample = bytesToInt16Bit(readBytes);
      int currentSample;
      int numIdentical = 0;   // # of consecutive samples with the same value
      long count = 1;          // The sample we're looking at
      int diffThreshold = (int)Math.pow(2, 16) / 2;
      System.out.println("Diff threshold: " + diffThreshold);
      while (ais.read(readBytes) != -1) {
        count++;
        currentSample = bytesToInt16Bit(readBytes);
        
        if (previousSample == currentSample) {
          numIdentical++;
        } else {
          if (numIdentical > 4) {
            errorPositions.add(count);
            // System.out.println("This many identical: " + numIdentical + " at " + count/48000);
          } 
          numIdentical = 0;
        }
        previousSample = currentSample;
      }


      // System.out.println("Buffer as int: " + (int)buf.getInt(0));

    } else if (ais.getFormat().getSampleSizeInBits() == 24) {
      // Code for 24 bit audio
      byte[] readBytes = new byte[3]; // read 3 bytes (1 frame) at a time
      for(int i = 0; i < 10; i++) {
        ais.read(readBytes);
        int blah = bytesToInt24Bit(readBytes);
        System.out.println(Integer.toString(i+1) + ". Sample as Int: " + blah);
      }
    }
  
  }


  // Ensures the given stream is in a format this program can work with
  // Takes in an AudioInputStream.  Returns true if the stream is any combination of
  // 16-bit, 24-bit, 44.1kHz, 48kHz, mono, or stero
  // Else, prints a summary of problems to console and returns false
  private static boolean checkStreamFormat(AudioInputStream ais) {
    ArrayList<String> problems = new ArrayList<String>(2);
    AudioFormat af = ais.getFormat();
    if (!af.getEncoding().equals(AudioFormat.Encoding.PCM_SIGNED))
      problems.add("File must be a PCM Signed audio file.  This file is: " + 
                    af.getEncoding());
    if (af.isBigEndian())
      problems.add("File must be little endian.  This file is big endian.");
    if ((af.getSampleRate() != 44100) && (af.getSampleRate() != 48000))
      problems.add("File sample rate must be 44.1kHz or 48kHz.  This file is: " +
                    af.getSampleRate() + "Hz");
    if ((af.getChannels() != 1) && (af.getChannels() != 2))
      problems.add("File must be mono or stereo.  This file has: " + 
                    af.getChannels() + " channels");
    if ((af.getSampleSizeInBits() != 16) && (af.getSampleSizeInBits() != 24))
      problems.add("File must be a 16 bit or 24 bit recording.  This file is: " +
                    af.getSampleSizeInBits() + " bit");
    for (String p : problems) {
      System.out.println(p);
    }
    return problems.size() == 0;
  }

  // Prints usage message and exits program
  private static void usage() {
    System.out.println("Usage: java DropoutDetector <filename.wav>");
    System.exit(1);
  }

  // Prints file read error message and exits program
  private static void readError() {
    System.out.println("Cannot read file.  Are the path and name correct?");
    System.exit(1);
  }

  // Takes a byte array of 2 bytes, interprets them as a signed short,
  // and returns that value as an int.
  // Assumes little-endian ordering
  // Invariant: byte array input must be of length 2
  private static int bytesToInt16Bit(byte[] bytes) {
    assert (bytes.length == 2);
    // Set up ByteBuffer
    ByteBuffer buf = ByteBuffer.allocate(2);
    buf.order(ByteOrder.LITTLE_ENDIAN);

    buf.put(bytes);
    return (int)buf.getShort(0);
  }

  // Takes a byte array of 3 bytes, pads with a 0x00 byte,
  // and returns those bytes interpreted as an int.
  // Assumes little-endian ordering
  // Invariant: byte array input must be of length 3
  private static int bytesToInt24Bit(byte[] bytes) {
    assert (bytes.length == 3);
    // Set up ByteBuffer
    ByteBuffer buf = ByteBuffer.allocate(4);
    buf.order(ByteOrder.LITTLE_ENDIAN);

    // byte[] testBytes = {0x7F, 0x7F, 0x7F};
    buf.put(bytes);
    // Pad with zero byte.  Because the byte order is little-endian
    // the zero will not affect the overall value when converted to int.    
    buf.put((byte)0x00);
    int result = buf.getInt(0);
    // Adjust for 2's complement
    return result > 8388607 ? result - 16777216 : result;
  }

}