
import { GoogleGenAI, Type, Modality } from "@google/genai";
import { Story, Scene, VideoMetadata, KnowledgeSource, VoiceID, NarrativeTone } from "../types";

const ai = new GoogleGenAI({ apiKey: process.env.API_KEY || "" });

export const suggestViralTopic = async (sources: KnowledgeSource[]): Promise<string> => {
  const sourcesContext = sources.map(s => s.url).join(", ");
  const response = await ai.models.generateContent({
    model: 'gemini-3-pro-preview',
    contents: `اقترح عنواناً فيروسياً واحداً فقط لفيديو يوتيوب طويل جذاب جداً بناءً على: [${sourcesContext}]. ركز على الغموض أو الحقائق الصادمة.`,
  });
  return response.text || "سر مخفي في أعماق التاريخ";
};

export const researchAndGenerateStory = async (
  topic: string, 
  sources: KnowledgeSource[],
  tone: NarrativeTone
): Promise<Story> => {
  const sourcesContext = sources.map(s => s.url).join(", ");
  
  const response = await ai.models.generateContent({
    model: 'gemini-3-pro-preview',
    contents: `أنت صانع أفلام وثائقية. اكتب رواية عن: "${topic}".
    المصادر: [${sourcesContext}].
    النبرة: ${tone}.
    
    قاعدة هامة: يجب أن يتكون كل مشهد من 150 إلى 200 كلمة لضمان أن يستغرق التعليق الصوتي دقيقة واحدة تقريباً لكل صورة.
    المطلوب 10 مشاهد (إجمالي 10 دقائق).
    
    أجب بتنسيق JSON حصراً.`,
    config: {
      tools: [{ googleSearch: {} }],
      responseMimeType: "application/json",
      responseSchema: {
        type: Type.OBJECT,
        properties: {
          title: { type: Type.STRING },
          description: { type: Type.STRING },
          narrativeStyle: { type: Type.STRING },
          scenes: {
            type: Type.ARRAY,
            items: {
              type: Type.OBJECT,
              properties: {
                text: { type: Type.STRING },
                imagePrompt: { type: Type.STRING }
              }
            }
          }
        },
        required: ["title", "description", "scenes", "narrativeStyle"]
      }
    }
  });

  const data = JSON.parse(response.text || "{}");
  return {
    ...data,
    scenes: data.scenes.map((s: any) => ({
      ...s,
      id: Math.random().toString(36).substr(2, 9)
    }))
  };
};

export const generateSceneImage = async (prompt: string): Promise<string> => {
  const response = await ai.models.generateContent({
    model: 'gemini-2.5-flash-image',
    contents: {
      parts: [{ text: `High-end cinematography, movie frame, 8k resolution, dramatic masterwork: ${prompt}` }]
    },
    config: {
      imageConfig: { aspectRatio: "16:9" }
    }
  });

  for (const part of response.candidates?.[0]?.content?.parts || []) {
    if (part.inlineData) {
      return `data:image/png;base64,${part.inlineData.data}`;
    }
  }
  throw new Error("Image failed");
};

export const generateTTS = async (text: string, voice: VoiceID, tone: NarrativeTone): Promise<string> => {
  const response = await ai.models.generateContent({
    model: "gemini-2.5-flash-preview-tts",
    contents: [{ parts: [{ text: `[STYLE: ${tone}] ${text}` }] }],
    config: {
      responseModalities: [Modality.AUDIO],
      speechConfig: {
        voiceConfig: {
          prebuiltVoiceConfig: { voiceName: voice }
        }
      }
    }
  });

  const base64Audio = response.candidates?.[0]?.content?.parts?.[0]?.inlineData?.data;
  if (!base64Audio) throw new Error("Audio failed");
  return base64Audio;
};

export const optimizeForYouTube = async (story: Story): Promise<VideoMetadata> => {
  const response = await ai.models.generateContent({
    model: 'gemini-3-pro-preview',
    contents: `قم بتحسين الفيديو: ${story.title} لليوتيوب. اعطني 3 عناوين فيروسية، وصف SEO كامل، ووسوم.`,
    config: {
      responseMimeType: "application/json",
      responseSchema: {
        type: Type.OBJECT,
        properties: {
          title: { type: Type.STRING },
          description: { type: Type.STRING },
          tags: { type: Type.ARRAY, items: { type: Type.STRING } },
          thumbnailIdeas: { type: Type.ARRAY, items: { type: Type.STRING } }
        }
      }
    }
  });
  return JSON.parse(response.text || "{}");
};

export const decode = (base64: string) => {
  const binaryString = atob(base64);
  const len = binaryString.length;
  const bytes = new Uint8Array(len);
  for (let i = 0; i < len; i++) {
    bytes[i] = binaryString.charCodeAt(i);
  }
  return bytes;
};

export const decodeAudioData = async (
  data: Uint8Array,
  ctx: AudioContext,
  sampleRate: number = 24000,
  numChannels: number = 1
): Promise<AudioBuffer> => {
  const dataInt16 = new Int16Array(data.buffer);
  const frameCount = dataInt16.length / numChannels;
  const buffer = ctx.createBuffer(numChannels, frameCount, sampleRate);
  for (let channel = 0; channel < numChannels; channel++) {
    const channelData = buffer.getChannelData(channel);
    for (let i = 0; i < frameCount; i++) {
      channelData[i] = dataInt16[i * numChannels + channel] / 32768.0;
    }
  }
  return buffer;
};
