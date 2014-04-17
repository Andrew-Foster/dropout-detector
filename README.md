dropout-detector
================

A Java command line app to assist in locating digital errors in uncompressed audio files, especially files transferred from Digital Audio Tape recordings. 

Searches for an uninterrupted series of identical samples in the given audio file.  Because of the inherent noise in real environments and recording equipment, this pattern is statistically very unlikely in a natural recording, so it indicates some type of digital manipulation, error, or clipping.  

False positives most often happen at the beginning and end of files, where complete silence (a long series of identical samples at 0) may have been inserted digitally.

-------------------

Limitations:
* Not guaranteed to find every possible digital error in an audio file.
* Only works on uncompressed PCM files
* Cannot find dropouts shorter than a few samples


-------------------

TODO (soon):
* Refine algorithm with sample data.  New patterns for finding dropouts may emerge.

TODO (later):
* Add GUI
* Support compressed file formats?
* Error detection/identification using machine learning? (would require huge set of example errors)
