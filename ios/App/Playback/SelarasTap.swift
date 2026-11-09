import AVFoundation
import BitChordCore
import MediaToolbox

/// Selaras on AVPlayer's audio: an `MTAudioProcessingTap` on the item's audio track hands every
/// decoded buffer to `SelarasProcessor` before it reaches the output. One tap (and one processor)
/// per player item; the processor reads the current settings from `SelarasStore` on each buffer.
enum SelarasTap {
    /// An audio mix carrying the tap, for `AVPlayerItem.audioMix`.
    static func audioMix(for item: AVPlayerItem) async -> AVAudioMix? {
        guard let track = try? await item.asset.loadTracks(withMediaType: .audio).first else { return nil }
        let box = TapBox()
        var callbacks = MTAudioProcessingTapCallbacks(
            version: kMTAudioProcessingTapCallbacksVersion_0,
            clientInfo: UnsafeMutableRawPointer(Unmanaged.passRetained(box).toOpaque()),
            init: tapInit,
            finalize: tapFinalize,
            prepare: tapPrepare,
            unprepare: tapUnprepare,
            process: tapProcess
        )
        var tapRef: Unmanaged<MTAudioProcessingTap>?
        let status = MTAudioProcessingTapCreate(kCFAllocatorDefault, &callbacks, kMTAudioProcessingTapCreationFlag_PostEffects, &tapRef)
        guard status == noErr, let tapRef else {
            Unmanaged.passUnretained(box).release()
            return nil
        }
        let tap = tapRef.takeRetainedValue()
        let parameters = AVMutableAudioMixInputParameters(track: track)
        parameters.audioTapProcessor = tap
        let mix = AVMutableAudioMix()
        mix.inputParameters = [parameters]
        return mix
    }
}

/// Lives as the tap's storage for as long as the tap does.
final class TapBox {
    let processor = SelarasProcessor()
    var channels = 2
    var interleaved = false
    /// Scratch planar buffers for interleaved formats, allocated in prepare, never on the render thread.
    var scratch: [UnsafeMutablePointer<Float>] = []
    var capacity = 0
    var isFloat = true

    init() {
        processor.spectrum = SelarasStore.shared.spectrum
    }

    func prepare(maxFrames: Int, format: AudioStreamBasicDescription) {
        channels = Int(format.mChannelsPerFrame)
        interleaved = format.mFormatFlags & kAudioFormatFlagIsNonInterleaved == 0
        isFloat = format.mFormatFlags & kAudioFormatFlagIsFloat != 0 && format.mBitsPerChannel == 32
        processor.configure(sampleRate: format.mSampleRate, channels: channels)
        freeScratch()
        capacity = maxFrames
        scratch = (0..<channels).map { _ in UnsafeMutablePointer<Float>.allocate(capacity: maxFrames) }
    }

    func freeScratch() {
        scratch.forEach { $0.deallocate() }
        scratch = []
        capacity = 0
    }

    func process(_ list: UnsafeMutablePointer<AudioBufferList>, frames: Int) {
        guard isFloat, frames > 0 else { return }
        let buffers = UnsafeMutableAudioBufferListPointer(list)
        if !interleaved {
            var planar: [UnsafeMutablePointer<Float>] = []
            planar.reserveCapacity(buffers.count)
            for buffer in buffers {
                guard let data = buffer.mData else { return }
                planar.append(data.assumingMemoryBound(to: Float.self))
            }
            processor.process(planar, frames: frames)
        } else if let data = buffers.first?.mData, frames <= capacity {
            let samples = data.assumingMemoryBound(to: Float.self)
            let ch = channels
            for c in 0..<ch { for i in 0..<frames { scratch[c][i] = samples[i * ch + c] } }
            processor.process(scratch, frames: frames)
            for c in 0..<ch { for i in 0..<frames { samples[i * ch + c] = scratch[c][i] } }
        }
    }

    deinit { freeScratch() }
}

private func box(_ tap: MTAudioProcessingTap) -> TapBox {
    Unmanaged<TapBox>.fromOpaque(MTAudioProcessingTapGetStorage(tap)).takeUnretainedValue()
}

private let tapInit: MTAudioProcessingTapInitCallback = { _, clientInfo, tapStorageOut in
    tapStorageOut.pointee = clientInfo
}

private let tapFinalize: MTAudioProcessingTapFinalizeCallback = { tap in
    Unmanaged<TapBox>.fromOpaque(MTAudioProcessingTapGetStorage(tap)).release()
}

private let tapPrepare: MTAudioProcessingTapPrepareCallback = { tap, maxFrames, format in
    box(tap).prepare(maxFrames: Int(maxFrames), format: format.pointee)
}

private let tapUnprepare: MTAudioProcessingTapUnprepareCallback = { tap in
    box(tap).freeScratch()
}

private let tapProcess: MTAudioProcessingTapProcessCallback = { tap, numberFrames, _, bufferList, numberFramesOut, flagsOut in
    let status = MTAudioProcessingTapGetSourceAudio(tap, numberFrames, bufferList, flagsOut, nil, numberFramesOut)
    guard status == noErr else { return }
    box(tap).process(bufferList, frames: Int(numberFramesOut.pointee))
}
