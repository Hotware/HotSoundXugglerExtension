package de.hotware.audio.data.ext;

import java.io.InputStream;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;

import com.xuggle.xuggler.IAudioSamples;
import com.xuggle.xuggler.ICodec;
import com.xuggle.xuggler.IContainer;
import com.xuggle.xuggler.IPacket;
import com.xuggle.xuggler.IStream;
import com.xuggle.xuggler.IStreamCoder;

import de.hotware.hotsound.audio.data.BaseAudio;

public class XugglerAudio extends BaseAudio {

	protected IContainer mContainer;
	protected InputStream mInputStream;
	protected IPacket mPacket;
	protected IStreamCoder mAudioCoder;
	protected int mAudioStreamId;
	private AudioFormat mAudioFormat;

	public XugglerAudio(InputStream pInputStream) {
		this(pInputStream, AudioSystem.NOT_SPECIFIED);
	}

	public XugglerAudio(InputStream pInputStream, long pFrameLength) {
		super();
		this.mInputStream = pInputStream;
		this.mPacket = IPacket.make();
	}

	@Override
	public AudioFormat getAudioFormat() {
		return this.mAudioFormat;
	}

	@Override
	public int read(byte[] pData, int pStart, int pLength) throws AudioException {
		IPacket packet = this.mPacket;
		int audioStreamId = this.mAudioStreamId;
		if(packet.getSize() > pLength) {
			throw new AudioException("packet size is greater than pLength");
		}
		while(this.mContainer.readNextPacket(packet) >= 0) {
			/*
			 * Now we have a packet, let's see if it belongs to our audio stream
			 */
			if(this.mPacket.getStreamIndex() == audioStreamId) {
				IAudioSamples samples = IAudioSamples.make(pLength,
						this.mAudioCoder.getChannels());
				/*
				 * Keep going until we've processed all data
				 */
				while(true) {
					int bytesDecoded = this.mAudioCoder.decodeAudio(samples,
							packet,
							0);
					if(bytesDecoded < 0) {
						throw new AudioException("couldn't decode correctly");
					}
					/*
					 * Some decoder will consume data in a packet, but will not
					 * be able to construct a full set of samples yet. Therefore
					 * you should always check if you got a complete set of
					 * samples from the decoder
					 */
					if(samples.isComplete()) {
						byte[] tmp = samples.getData().getByteArray(0,
								samples.getSize());
						for(int i = 0; i < tmp.length; ++i) {
							pData[i] = tmp[i];
						}
						return tmp.length;
					}
				}
			} else {
				/*
				 * This packet isn't part of our audio stream, so we just
				 * silently drop it.
				 */
				do {
				} while(false);
			}

		}
		return -1;
	}

	@SuppressWarnings("deprecation")
	@Override
	public void open() throws AudioException {
		this.mContainer = IContainer.make();

		if(this.mContainer.open(this.mInputStream, null) < 0) {
			throw new AudioException("couldn't open inputstream");
		}

		// query how many streams the call to open found
		int numStreams = this.mContainer.getNumStreams();

		// and iterate through the streams to find the first audio stream
		this.mAudioStreamId = -1;
		IStreamCoder audioCoder = null;
		for(int i = 0; i < numStreams; i++) {
			// Find the stream object
			IStream stream = this.mContainer.getStream(i);
			// Get the pre-configured decoder that can decode this stream;
			IStreamCoder coder = stream.getStreamCoder();

			if(coder.getCodecType() == ICodec.Type.CODEC_TYPE_AUDIO) {
				this.mAudioStreamId = i;
				audioCoder = coder;
				break;
			}
		}
		if(this.mAudioStreamId == -1) {
			throw new AudioException("couldn't find the audiostream");
		}

		/*
		 * Now we have found the audio stream in this file. Let's open up our
		 * decoder so it can do work.
		 */
		if(audioCoder.open() < 0) {
			throw new AudioException("couldn't open the audiocoder");
		}

		this.mAudioCoder = audioCoder;

		this.mAudioFormat = new AudioFormat(this.mAudioCoder.getSampleRate(),
				(int) IAudioSamples.findSampleBitDepth(this.mAudioCoder
						.getSampleFormat()),
				this.mAudioCoder.getChannels(),
				true, /* xuggler defaults to signed 16 bit samples */
				false);

	}

	@Override
	public void close() throws AudioException {
		super.close();
		if(this.mAudioCoder != null) {
			this.mAudioCoder.close();
			this.mAudioCoder = null;
		}
		if(this.mContainer != null) {
			this.mContainer.close();
			this.mContainer = null;
		}
	}

}
