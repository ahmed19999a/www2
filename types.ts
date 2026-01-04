
export type VoiceID = 'Puck' | 'Charon' | 'Kore' | 'Zephyr' | 'Fenrir';
export type NarrativeTone = 'documentary' | 'happy' | 'sad' | 'calm' | 'literary' | 'dramatic';
export type SubtitleAnimation = 'typing' | 'fade' | 'pop' | 'classic';
export type FontSize = 'small' | 'medium' | 'large' | 'huge';

export interface Scene {
  id: string;
  text: string;
  imagePrompt: string;
  imageUrl?: string;
  audioData?: string;
  duration?: number;
}

export interface VideoMetadata {
  title: string;
  description: string;
  tags: string[];
  thumbnailIdeas: string[];
}

export interface SubtitleConfig {
  animation: SubtitleAnimation;
  fontSize: FontSize;
  position: 'bottom' | 'center';
  color: string;
}

export interface Project {
  id: string;
  topic: string;
  status: 'draft' | 'generating' | 'completed' | 'failed';
  story?: Story;
  metadata?: VideoMetadata;
  createdAt: number;
  preferences: {
    voice: VoiceID;
    tone: NarrativeTone;
    subtitle: SubtitleConfig;
  };
}

export interface Story {
  title: string;
  scenes: Scene[];
  description: string;
  narrativeStyle: string;
}

export interface KnowledgeSource {
  id: string;
  type: 'youtube' | 'article' | 'manual';
  url: string;
  title: string;
  addedAt: number;
}

export enum GenerationStep {
  IDLE = 'IDLE',
  RESEARCHING = 'RESEARCHING',
  WRITING = 'WRITING',
  ILLUSTRATING = 'ILLUSTRATING',
  NARRATING = 'NARRATING',
  OPTIMIZING = 'OPTIMIZING',
  READY = 'READY'
}

export interface GenerationProgress {
  step: GenerationStep;
  percentage: number;
  message: string;
}
