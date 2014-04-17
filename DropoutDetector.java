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
  private static void checkStreamForDropouts(AudioInputStream ais) 
                                          throws java.io.IOException {
    int numChannels = ais.getFormat().getChannels();
    ArrayList<Dropout> errors = new ArrayList<Dropout>();
    // The number of bytes per read equals the stream's frame size
    byte[] readBytes = new byte[ais.getFormat().getFrameSize()];
    
    // //// Code to test the retrieval of the first sample ////
    // ais.read(readBytes);
    // int[] samples = bytesToInts(readBytes, numChannels);
    // System.out.println("First Samples:");
    // System.out.println("" + samples.length + " samples found");
    // for (int i = 0; i < samples.length; i++) {
    //   System.out.println("-- " + i + ". " + samples[i]);
    // }

    int[] currentSamples;
    int[] numIdentical = new int[numChannels];   // # of consecutive samples with the same value
    long count = 1;               // The sample's location in the file

    ais.read(readBytes);
    int[] previousSamples = bytesToInts(readBytes, numChannels);
    while (ais.read(readBytes) != -1) {
      count++;
      currentSamples = bytesToInts(readBytes, numChannels);
      
      // check sample values of each channel against their previous sample value
      for(int i = 0; i < numChannels; i++) {
        if (previousSamples[i] == currentSamples[i]) {
          numIdentical[i] = numIdentical[i] + 1;
        } else {
          if (numIdentical[i] > 4) {
            errors.add(new Dropout(count, i, numIdentical[i]));
            // System.out.println("This many identical: " + numIdentical + " at " + count/48000);
          } 
          numIdentical[i] = 0;
        }
      }
      previousSamples = currentSamples;
    }

    if (errors.size() == 0) {
      System.out.println("No Dropouts Detected.");
    } else {
      double region = -2;
      for (Dropout dOut : errors) {
        double position = (double)dOut.position / ais.getFormat().getSampleRate();
        if ((position - region) > 1) { // at least 1 second from previous dropout
          System.out.println("Dropout at " + position + "seconds:");
          System.out.println("-- Channel " + dOut.channel);
          System.out.println("-- " + dOut.numConsecutive + " samples");
          region = position;
        }
        

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

  /*
  * Takes an array of bytes and a number of channels.
  * The bytes represent a single PCM audio frame containing sample values for
  * all channels as two's complement signed integers of varying numbers of bytes, 
  * arranged sequentially by channel
  * 
  * Returns an array of ints where each int represents
  * the value of a single sample for the channel at the same index.
  */
  private static int[] bytesToInts(byte[] bytes, int numChannels) {
    int sampleSize = bytes.length / numChannels; // length (in bytes) of each sample
    int[] result = new int[numChannels];
    int padBytes = 4 - sampleSize;               // pad bytes needed to bring sampleSize to 4 bytes
    assert (bytes.length > 0);                   // Bytes must contain at least 1 byte
    assert (bytes.length % numChannels == 0);    // # of channels must divide evenly into # of bytes
    assert (bytes.length >= numChannels);        // Must be at least as many bytes as channels
    assert (sampleSize <= 4);                    // Number must fit in an int (4 bytes)

    // For each channel
    for (int i = 0; i < numChannels; i++) {
      // Set up buffer
      ByteBuffer buf = ByteBuffer.allocate(4);
      buf.order(ByteOrder.LITTLE_ENDIAN);
      
      // Pad buffer
      for (int j = 0; j < padBytes; j++) {
        // Pad beginning of buffer (least significant digits).
        // The point of this is to ensure that even if the sampleSize is only
        // 1, 2, or 3 bytes, the most significant bit of that sample
        // will still be the most significant bit when it is interpreted
        // as a two's complement 32-bit integer.
        // Otherwise, the value would be interpreted incorrectly.
        // Later, we'll shift right to undo the padding step.
        buf.put((byte)0x00);
      }

      // Add data to Buffer
      for (int j = 0; j < sampleSize; j++) {
        buf.put(bytes[(i*sampleSize) + j]); // Offset by the current channel
      }

      // Get the int representation
      result[i] = buf.getInt(0);
      // System.out.println("channel " + i + ", before shift: " + result[i]);
      
      // Shift back to undo padding step
      result[i] = result[i] >> (padBytes * 4);
      // System.out.println("channel " + i + ", after shift: " + result[i]);
    }
    return result;
  }
}


// The Dropout class represents an instance of an audio dropout error.
// position: which sample in the file it occurred.
// channel: the channel on which it occurred (for stereo: 0 - left, 1 - right)
// numConsecutive: how many consecutive identical samples are in this dropout
class Dropout {
  long position;
  int channel;
  int numConsecutive;

  public Dropout(long position, int channel, int numConsecutive) {
    this.position = position;
    this.channel = channel;
    this.numConsecutive = numConsecutive;
  }
}