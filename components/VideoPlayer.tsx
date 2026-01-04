
import React, { useState, useEffect, useRef, useCallback } from 'react';
import { Story, Scene, SubtitleConfig } from '../types';
import { decode, decodeAudioData } from '../services/geminiService';

interface VideoPlayerProps {
  story: Story;
  subtitleConfig: SubtitleConfig;
}

const VideoPlayer: React.FC<VideoPlayerProps> = ({ story, subtitleConfig }) => {
  const [currentSceneIndex, setCurrentSceneIndex] = useState(0);
  const [isPlaying, setIsPlaying] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [totalDuration, setTotalDuration] = useState(0);
  const [displayedText, setDisplayedText] = useState("");

  const audioContextRef = useRef<AudioContext | null>(null);
  const sourceNodeRef = useRef<AudioBufferSourceNode | null>(null);
  const timerRef = useRef<number | null>(null);
  const startTimeRef = useRef<number>(0);

  useEffect(() => {
    const total = story.scenes.reduce((acc, scene) => acc + (scene.duration || 60), 0);
    setTotalDuration(total);
  }, [story]);

  // Typing Effect logic
  useEffect(() => {
    const fullText = story.scenes[currentSceneIndex]?.text || "";
    if (subtitleConfig.animation === 'typing') {
      setDisplayedText("");
      let i = 0;
      const interval = setInterval(() => {
        setDisplayedText(fullText.slice(0, i));
        i++;
        if (i > fullText.length) clearInterval(interval);
      }, 50);
      return () => clearInterval(interval);
    } else {
      setDisplayedText(fullText);
    }
  }, [currentSceneIndex, story.scenes, subtitleConfig.animation]);

  const initAudio = () => {
    if (!audioContextRef.current) {
      audioContextRef.current = new (window.AudioContext || (window as any).webkitAudioContext)({ sampleRate: 24000 });
    }
  };

  const playScene = useCallback(async (index: number, offset: number = 0) => {
    initAudio();
    const scene = story.scenes[index];
    if (!scene || !scene.audioData) return;

    if (sourceNodeRef.current) sourceNodeRef.current.stop();

    const ctx = audioContextRef.current!;
    const bytes = decode(scene.audioData);
    const buffer = await decodeAudioData(bytes, ctx);
    
    const source = ctx.createBufferSource();
    source.buffer = buffer;
    source.connect(ctx.destination);
    
    source.onended = () => {
      if (index < story.scenes.length - 1) {
        setCurrentSceneIndex(index + 1);
        playScene(index + 1);
      } else {
        setIsPlaying(false);
        setCurrentSceneIndex(0);
      }
    };

    source.start(0, offset);
    sourceNodeRef.current = source;
    startTimeRef.current = ctx.currentTime - offset;
  }, [story.scenes]);

  const togglePlay = () => {
    if (isPlaying) {
      sourceNodeRef.current?.stop();
      setIsPlaying(false);
    } else {
      setIsPlaying(true);
      playScene(currentSceneIndex);
    }
  };

  const getFontSizeClass = () => {
    switch(subtitleConfig.fontSize) {
      case 'small': return 'text-sm md:text-base';
      case 'medium': return 'text-lg md:text-xl';
      case 'large': return 'text-2xl md:text-3xl';
      case 'huge': return 'text-4xl md:text-5xl';
      default: return 'text-xl';
    }
  };

  return (
    <div className="relative group w-full aspect-video rounded-3xl overflow-hidden shadow-2xl border-4 border-slate-800 bg-black">
      <div className="absolute inset-0">
        {story.scenes.map((scene, idx) => (
          <img 
            key={scene.id}
            src={scene.imageUrl} 
            className={`absolute inset-0 w-full h-full object-cover transition-opacity duration-[2000ms] ${idx === currentSceneIndex ? 'opacity-100 scale-110' : 'opacity-0'}`}
            style={{ transitionTimingFunction: 'cubic-bezier(0.4, 0, 0.2, 1)' }}
          />
        ))}
        <div className="absolute inset-0 bg-gradient-to-t from-black/90 via-transparent to-black/30"></div>
      </div>

      {/* Dynamic Subtitles */}
      <div className={`absolute left-0 right-0 flex justify-center px-12 z-10 pointer-events-none transition-all duration-500 ${subtitleConfig.position === 'center' ? 'top-1/2 -translate-y-1/2' : 'bottom-24'}`}>
        <p 
          className={`bg-black/40 backdrop-blur-lg text-white font-bold px-8 py-4 rounded-2xl border border-white/10 text-center leading-relaxed shadow-2xl ${getFontSizeClass()} ${subtitleConfig.animation === 'fade' ? 'animate-pulse' : ''}`}
          style={{ color: subtitleConfig.color }}
        >
          {displayedText}
        </p>
      </div>

      <div className="absolute bottom-0 left-0 right-0 p-6 flex items-center gap-4 bg-gradient-to-t from-black to-transparent opacity-0 group-hover:opacity-100 transition-opacity">
        <button onClick={togglePlay} className="w-12 h-12 flex items-center justify-center bg-cyan-500 text-white rounded-full hover:scale-110 transition-transform">
          <i className={`fas ${isPlaying ? 'fa-pause' : 'fa-play'}`}></i>
        </button>
        <div className="flex-1 h-1.5 bg-white/20 rounded-full overflow-hidden">
          <div className="h-full bg-cyan-500 transition-all duration-500" style={{ width: `${(currentSceneIndex / story.scenes.length) * 100}%` }}></div>
        </div>
      </div>
    </div>
  );
};

export default VideoPlayer;
