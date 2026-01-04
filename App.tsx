
import React, { useState, useEffect, useRef } from 'react';
import { 
  researchAndGenerateStory, 
  generateSceneImage, 
  generateTTS, 
  optimizeForYouTube,
  suggestViralTopic,
  decode, 
  decodeAudioData 
} from './services/geminiService';
import { 
  Story, GenerationStep, GenerationProgress, 
  Scene, KnowledgeSource, Project, VideoMetadata,
  VoiceID, NarrativeTone, SubtitleConfig, SubtitleAnimation, FontSize 
} from './types';
import VideoPlayer from './components/VideoPlayer';

const VOICES: { id: VoiceID; label: string; gender: 'male' | 'female'; desc: string }[] = [
  { id: 'Puck', label: 'باك', gender: 'male', desc: 'عميق وجذاب' },
  { id: 'Kore', label: 'كوري', gender: 'female', desc: 'واضح وسردي' },
  { id: 'Charon', label: 'شارون', gender: 'male', desc: 'رزين ووقور' },
];

const SUBTITLE_ANIMS: { id: SubtitleAnimation; label: string }[] = [
  { id: 'typing', label: 'كتابة حية' },
  { id: 'fade', label: 'تلاشي' },
  { id: 'classic', label: 'كلاسيكي' },
];

const App: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'create' | 'agent' | 'youtube' | 'library'>('create');
  const [topic, setTopic] = useState('');
  const [selectedVoice, setSelectedVoice] = useState<VoiceID>('Puck');
  const [selectedTone, setSelectedTone] = useState<NarrativeTone>('documentary');
  const [subConfig, setSubConfig] = useState<SubtitleConfig>({
    animation: 'typing',
    fontSize: 'large',
    position: 'bottom',
    color: '#ffffff'
  });
  
  const [isYoutubeConnected, setIsYoutubeConnected] = useState(false);
  const [sources, setSources] = useState<KnowledgeSource[]>([]);
  const [newSourceUrl, setNewSourceUrl] = useState('');
  const [projects, setProjects] = useState<Project[]>([]);
  const [currentProject, setCurrentProject] = useState<Project | null>(null);
  const [progress, setProgress] = useState<GenerationProgress>({
    step: GenerationStep.IDLE,
    percentage: 0,
    message: ''
  });

  const audioContextRef = useRef<AudioContext | null>(null);

  const connectYoutube = () => {
    // Simulated connection
    setTimeout(() => setIsYoutubeConnected(true), 1500);
  };

  const startBatchProduction = async (manualTopic?: string) => {
    let finalTopic = manualTopic || topic;
    if (!finalTopic) {
      setProgress({ step: GenerationStep.RESEARCHING, percentage: 5, message: 'الوكيل يحلل الترند لإنتاج محتوى فيروسي...' });
      finalTopic = await suggestViralTopic(sources);
      setTopic(finalTopic);
    }

    if (!audioContextRef.current) {
      audioContextRef.current = new (window.AudioContext || (window as any).webkitAudioContext)({ sampleRate: 24000 });
    }

    const projectId = Math.random().toString(36).substr(2, 9);
    const newProject: Project = {
      id: projectId, topic: finalTopic, status: 'generating', createdAt: Date.now(),
      preferences: { voice: selectedVoice, tone: selectedTone, subtitle: subConfig }
    };
    setProjects([newProject, ...projects]);
    setCurrentProject(newProject);

    try {
      setProgress({ step: GenerationStep.WRITING, percentage: 10, message: `جاري كتابة رواية سينمائية طويلة: "${finalTopic}"...` });
      const story = await researchAndGenerateStory(finalTopic, sources, selectedTone);
      
      const totalScenes = story.scenes.length;
      for (let i = 0; i < totalScenes; i++) {
        const sceneProgress = 15 + Math.floor((i / totalScenes) * 75);
        setProgress({ 
          step: GenerationStep.ILLUSTRATING, percentage: sceneProgress, 
          message: `إنتاج الدقيقة ${i + 1}: توليد الصورة والمعالجة الصوتية...` 
        });

        const scene = story.scenes[i];
        scene.imageUrl = await generateSceneImage(scene.imagePrompt);
        const audioBase64 = await generateTTS(scene.text, selectedVoice, selectedTone);
        scene.audioData = audioBase64;
        
        const bytes = decode(audioBase64);
        const buffer = await decodeAudioData(bytes, audioContextRef.current!);
        scene.duration = buffer.duration;
      }

      setProgress({ step: GenerationStep.OPTIMIZING, percentage: 95, message: 'تجهيز سيو اليوتيوب والصور المصغرة...' });
      const metadata = await optimizeForYouTube(story);

      const completedProject: Project = { ...newProject, status: 'completed', story, metadata };
      setProjects(prev => prev.map(p => p.id === projectId ? completedProject : p));
      setCurrentProject(completedProject);
      setProgress({ step: GenerationStep.READY, percentage: 100, message: 'الفيلم جاهز للنشر!' });
    } catch (error) {
      console.error(error);
      setProgress({ step: GenerationStep.IDLE, percentage: 0, message: 'فشل الإنتاج.' });
    }
  };

  return (
    <div className="min-h-screen bg-[#05070a] text-slate-200 flex flex-col font-['Cairo']">
      <div className="flex flex-1 overflow-hidden">
        {/* Sidebar */}
        <aside className="w-20 md:w-72 bg-[#0c1017] border-l border-white/5 p-6 flex flex-col gap-8 shadow-2xl z-20">
          <div className="flex items-center gap-3 px-2">
            <div className="w-12 h-12 bg-cyan-600 rounded-2xl flex items-center justify-center shadow-lg shadow-cyan-600/30">
              <i className="fas fa-video text-white text-2xl"></i>
            </div>
            <div className="hidden md:block">
              <p className="font-black text-xl tracking-tighter text-white">PRO CINEMA</p>
              <p className="text-[10px] text-cyan-500 font-bold uppercase tracking-widest">AI Video Studio</p>
            </div>
          </div>

          <nav className="space-y-2">
            {[
              { id: 'create', icon: 'fa-plus-circle', label: 'استوديو الإنتاج' },
              { id: 'agent', icon: 'fa-brain', label: 'ذكاء الوكيل' },
              { id: 'youtube', icon: 'fa-brands fa-youtube', label: 'قناة يوتيوب' },
              { id: 'library', icon: 'fa-film', label: 'المكتبة' }
            ].map(item => (
              <button
                key={item.id}
                onClick={() => setActiveTab(item.id as any)}
                className={`w-full flex items-center gap-4 px-4 py-3.5 rounded-2xl transition-all ${activeTab === item.id ? 'bg-cyan-600 text-white shadow-xl shadow-cyan-600/20' : 'text-slate-500 hover:bg-white/5'}`}
              >
                <i className={`fas ${item.icon} text-lg`}></i>
                <span className="hidden md:block font-bold">{item.label}</span>
              </button>
            ))}
          </nav>

          <div className="mt-auto space-y-4">
             <div className="bg-white/5 p-4 rounded-2xl border border-white/5 hidden md:block">
                <p className="text-[10px] text-slate-500 uppercase font-bold mb-3 tracking-widest">الأدوات المتصلة</p>
                <div className="space-y-2 text-[10px] font-bold">
                   <div className="flex justify-between items-center text-cyan-400">
                      <span>Gemini 3.0 Pro</span> <i className="fas fa-check"></i>
                   </div>
                   <div className="flex justify-between items-center text-cyan-400">
                      <span>Image Gen (8K)</span> <i className="fas fa-check"></i>
                   </div>
                   <div className="flex justify-between items-center text-cyan-400">
                      <span>TTS High-Def</span> <i className="fas fa-check"></i>
                   </div>
                   <div className={`flex justify-between items-center ${isYoutubeConnected ? 'text-green-500' : 'text-slate-600'}`}>
                      <span>YouTube API</span> <i className={`fas ${isYoutubeConnected ? 'fa-check' : 'fa-times'}`}></i>
                   </div>
                </div>
             </div>
          </div>
        </aside>

        <main className="flex-1 overflow-y-auto p-6 md:p-12 relative">
          {activeTab === 'create' && (
            <div className="max-w-6xl mx-auto space-y-12">
              <header className="flex flex-col md:flex-row md:items-center justify-between gap-6">
                <div>
                  <h1 className="text-5xl font-black text-white mb-2 leading-tight">صناعة المحتوى <span className="text-cyan-500">الاحترافي</span></h1>
                  <p className="text-slate-400 text-lg">تحكم كامل في القصة، الصوت، وطريقة عرض النصوص.</p>
                </div>
              </header>

              {progress.step === GenerationStep.IDLE ? (
                <div className="grid grid-cols-1 xl:grid-cols-3 gap-10">
                  <div className="xl:col-span-2 space-y-8">
                    <div className="bg-[#0c1017] p-8 rounded-[40px] border border-white/5 shadow-2xl">
                      <label className="block text-sm font-black text-slate-500 mb-6 uppercase tracking-widest">موضوع الفيديو (أو اتركه للتوليد التلقائي)</label>
                      <textarea 
                        value={topic}
                        onChange={(e) => setTopic(e.target.value)}
                        placeholder="اكتب فكرة القصة هنا... مثلاً: لغز اختفاء طائرة فوق المحيط بنبرة وثائقية حزينة."
                        className="w-full h-56 bg-black/40 border border-white/10 rounded-3xl p-8 text-2xl focus:ring-2 focus:ring-cyan-500 outline-none transition-all placeholder:text-slate-800"
                      />
                    </div>

                    <div className="flex flex-col md:flex-row gap-6">
                       <button onClick={() => startBatchProduction()} className="flex-1 bg-cyan-600 hover:bg-cyan-500 text-white font-black py-6 rounded-3xl text-xl shadow-2xl shadow-cyan-600/30 transition-all flex items-center justify-center gap-4">
                          <i className="fas fa-magic text-2xl"></i> توليد ذكي فيروسي
                       </button>
                       <button onClick={() => startBatchProduction(topic)} disabled={!topic} className="flex-1 bg-slate-800 hover:bg-slate-700 disabled:opacity-30 text-white font-black py-6 rounded-3xl text-xl transition-all">
                          إنتاج فكرتي يدوياً
                       </button>
                    </div>
                  </div>

                  <div className="space-y-8">
                    {/* Subtitle Controls */}
                    <div className="bg-[#0c1017] p-8 rounded-[40px] border border-white/5 shadow-xl">
                      <h3 className="font-black text-lg text-white mb-6 uppercase flex items-center gap-3">
                        <i className="fas fa-closed-captioning text-cyan-500"></i> إعدادات النصوص
                      </h3>
                      <div className="space-y-6">
                        <div>
                          <p className="text-[10px] text-slate-500 font-bold uppercase mb-3">حركة الظهور</p>
                          <div className="grid grid-cols-3 gap-2">
                             {SUBTITLE_ANIMS.map(anim => (
                               <button 
                                 key={anim.id}
                                 onClick={() => setSubConfig({...subConfig, animation: anim.id})}
                                 className={`py-2 rounded-xl text-[10px] font-bold border transition-all ${subConfig.animation === anim.id ? 'bg-cyan-500/20 border-cyan-500 text-cyan-400' : 'bg-black/20 border-white/5 text-slate-500'}`}
                               >
                                 {anim.label}
                               </button>
                             ))}
                          </div>
                        </div>
                        <div>
                          <p className="text-[10px] text-slate-500 font-bold uppercase mb-3">حجم الخط</p>
                          <div className="grid grid-cols-4 gap-2">
                             {(['small', 'medium', 'large', 'huge'] as FontSize[]).map(size => (
                               <button 
                                 key={size}
                                 onClick={() => setSubConfig({...subConfig, fontSize: size})}
                                 className={`py-2 rounded-xl text-[10px] font-bold border transition-all uppercase ${subConfig.fontSize === size ? 'bg-cyan-500/20 border-cyan-500 text-cyan-400' : 'bg-black/20 border-white/5 text-slate-500'}`}
                               >
                                 {size}
                               </button>
                             ))}
                          </div>
                        </div>
                        <div>
                          <p className="text-[10px] text-slate-500 font-bold uppercase mb-3">لون النص</p>
                          <div className="flex gap-2">
                             {['#ffffff', '#fbbf24', '#22d3ee', '#f472b6'].map(color => (
                               <button 
                                 key={color}
                                 onClick={() => setSubConfig({...subConfig, color})}
                                 className={`w-8 h-8 rounded-full border-2 transition-all ${subConfig.color === color ? 'border-white scale-110' : 'border-transparent'}`}
                                 style={{ backgroundColor: color }}
                               />
                             ))}
                          </div>
                        </div>
                      </div>
                    </div>

                    <div className="bg-[#0c1017] p-8 rounded-[40px] border border-white/5">
                       <h3 className="font-black text-lg text-white mb-6 uppercase"><i className="fas fa-microphone-alt text-cyan-500 ml-2"></i> صوت المعلق</h3>
                       <div className="space-y-2">
                          {VOICES.map(v => (
                            <button 
                              key={v.id}
                              onClick={() => setSelectedVoice(v.id)}
                              className={`w-full flex items-center justify-between p-4 rounded-2xl border transition-all ${selectedVoice === v.id ? 'bg-cyan-600/20 border-cyan-500 text-cyan-400' : 'bg-black/20 border-white/5 hover:bg-white/5 text-slate-500'}`}
                            >
                              <div className="text-right">
                                <p className="text-xs font-bold text-white">{v.label}</p>
                                <p className="text-[10px]">{v.desc}</p>
                              </div>
                              <i className={`fas ${v.gender === 'male' ? 'fa-mars' : 'fa-venus'} opacity-20`}></i>
                            </button>
                          ))}
                       </div>
                    </div>
                  </div>
                </div>
              ) : progress.step !== GenerationStep.READY ? (
                <div className="bg-[#0c1017] p-24 rounded-[60px] border border-white/5 text-center space-y-12">
                   <div className="relative w-64 h-64 mx-auto">
                      <svg className="w-full h-full transform -rotate-90" viewBox="0 0 100 100">
                        <circle cx="50" cy="50" r="45" fill="none" stroke="rgba(255,255,255,0.02)" strokeWidth="4" />
                        <circle cx="50" cy="50" r="45" fill="none" stroke="#0891b2" strokeWidth="4" strokeDasharray={`${progress.percentage * 2.8} 280`} strokeLinecap="round" className="transition-all duration-1000 shadow-[0_0_30px_rgba(8,145,178,0.5)]" />
                      </svg>
                      <div className="absolute inset-0 flex flex-col items-center justify-center">
                        <span className="text-6xl font-black text-white">{progress.percentage}%</span>
                        <span className="text-xs text-slate-500 font-bold uppercase tracking-widest mt-2">Rendering Project</span>
                      </div>
                   </div>
                   <h2 className="text-4xl font-bold text-white tracking-tight">{progress.message}</h2>
                </div>
              ) : (
                <div className="space-y-12 animate-in fade-in slide-in-from-bottom-12 duration-1000">
                  <VideoPlayer story={currentProject?.story!} subtitleConfig={subConfig} />
                  
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-10">
                    <div className="bg-[#0c1017] p-10 rounded-[40px] border border-white/5 shadow-2xl">
                      <h3 className="font-black text-2xl text-cyan-400 mb-8 uppercase tracking-tighter">YouTube Optimization</h3>
                      <div className="space-y-6">
                        <div className="bg-black/40 p-6 rounded-3xl border border-white/5">
                           <p className="text-[10px] text-slate-500 font-bold uppercase mb-2">أفضل عنوان فيروسي</p>
                           <p className="text-xl font-bold text-white leading-snug">{currentProject?.metadata?.title}</p>
                        </div>
                        <div className="bg-black/40 p-6 rounded-3xl border border-white/5">
                           <p className="text-[10px] text-slate-500 font-bold uppercase mb-2">وصف SEO</p>
                           <p className="text-sm text-slate-400 line-clamp-6 leading-relaxed">{currentProject?.metadata?.description}</p>
                        </div>
                      </div>
                      <div className="mt-8">
                         <button 
                           onClick={() => setActiveTab('youtube')}
                           className="w-full bg-red-600 hover:bg-red-500 text-white font-black py-5 rounded-2xl flex items-center justify-center gap-3 transition-all"
                         >
                           <i className="fab fa-youtube text-2xl"></i> الانتقال للنشر في يوتيوب
                         </button>
                      </div>
                    </div>

                    <div className="bg-[#0c1017] p-10 rounded-[40px] border border-white/5 shadow-2xl">
                      <h3 className="font-black text-2xl text-cyan-400 mb-8 uppercase tracking-tighter">Thumbnail Mastery</h3>
                      <div className="space-y-4">
                        {currentProject?.metadata?.thumbnailIdeas.map((idea, i) => (
                          <div key={i} className="flex gap-4 p-5 bg-white/5 rounded-3xl border border-white/5 items-center hover:bg-white/10 transition-all">
                            <div className="w-12 h-12 bg-cyan-600 rounded-2xl flex items-center justify-center font-black text-white shrink-0">V{i+1}</div>
                            <p className="text-sm text-slate-300 font-bold leading-relaxed">{idea}</p>
                          </div>
                        ))}
                      </div>
                    </div>
                  </div>
                </div>
              )}
            </div>
          )}

          {activeTab === 'youtube' && (
            <div className="max-w-4xl mx-auto space-y-10">
               <header>
                <h1 className="text-5xl font-black text-white mb-2 leading-tight">مركز <span className="text-red-500">يوتيوب</span></h1>
                <p className="text-slate-400">اربط قناتك وانشر فيديوهاتك المصنوعة بالذكاء الاصطناعي مباشرة.</p>
              </header>

              <div className="bg-[#0c1017] p-12 rounded-[50px] border border-white/5 shadow-2xl text-center">
                {!isYoutubeConnected ? (
                  <div className="space-y-8">
                    <div className="w-24 h-24 bg-red-600/10 text-red-600 rounded-full flex items-center justify-center mx-auto text-4xl shadow-[0_0_40px_rgba(220,38,38,0.1)]">
                      <i className="fab fa-youtube"></i>
                    </div>
                    <div className="space-y-2">
                      <h2 className="text-3xl font-bold text-white">قناتك غير مرتبطة</h2>
                      <p className="text-slate-500 max-w-md mx-auto">للنشر التلقائي، يجب منح الصلاحية للوكيل الذكي للوصول إلى استوديو يوتيوب الخاص بك.</p>
                    </div>
                    <button 
                      onClick={connectYoutube}
                      className="bg-red-600 hover:bg-red-500 text-white font-black py-5 px-12 rounded-3xl text-xl shadow-2xl shadow-red-600/20 transition-all active:scale-95"
                    >
                      ربط القناة الآن
                    </button>
                  </div>
                ) : (
                  <div className="space-y-8">
                     <div className="flex items-center justify-center gap-4 bg-green-500/10 p-4 rounded-full border border-green-500/20 w-fit mx-auto">
                        <div className="w-3 h-3 bg-green-500 rounded-full animate-pulse"></div>
                        <span className="text-green-500 font-bold uppercase tracking-widest text-xs">قناتك متصلة وجاهزة</span>
                     </div>
                     <div className="bg-black/40 p-8 rounded-[40px] border border-white/5 flex flex-col md:flex-row items-center gap-8 text-right">
                        <div className="w-24 h-24 bg-slate-800 rounded-full border-4 border-cyan-500/30 overflow-hidden shrink-0">
                           <img src="https://api.dicebear.com/7.x/avataaars/svg?seed=creator" className="w-full h-full object-cover" />
                        </div>
                        <div className="flex-1">
                           <h3 className="text-2xl font-bold text-white mb-1">استوديو المبدع الذكي</h3>
                           <p className="text-slate-500 text-sm">@ai_cinema_creator • 24.5K مشترك</p>
                        </div>
                        <button className="bg-white/5 hover:bg-white/10 text-white py-3 px-6 rounded-2xl border border-white/5 font-bold transition-all text-xs uppercase tracking-widest">إعادة المزامنة</button>
                     </div>
                     <div className="grid grid-cols-3 gap-4">
                        <div className="bg-black/20 p-6 rounded-3xl border border-white/5">
                           <p className="text-2xl font-black text-white">1.2M</p>
                           <p className="text-[10px] text-slate-500 uppercase font-bold tracking-widest mt-1">المشاهدات</p>
                        </div>
                        <div className="bg-black/20 p-6 rounded-3xl border border-white/5">
                           <p className="text-2xl font-black text-white">45</p>
                           <p className="text-[10px] text-slate-500 uppercase font-bold tracking-widest mt-1">الفيديوهات</p>
                        </div>
                        <div className="bg-black/20 p-6 rounded-3xl border border-white/5">
                           <p className="text-2xl font-black text-white">88%</p>
                           <p className="text-[10px] text-slate-500 uppercase font-bold tracking-widest mt-1">نسبة النقر</p>
                        </div>
                     </div>
                  </div>
                )}
              </div>
            </div>
          )}

          {activeTab === 'agent' && (
            <div className="max-w-4xl mx-auto space-y-10">
              <header>
                <h1 className="text-5xl font-black text-white mb-2 leading-tight">تطوير <span className="text-cyan-500">الوكيل</span></h1>
                <p className="text-slate-400">تزويد الوكيل بمصادر التعلم لزيادة جودة ودقة القصص.</p>
              </header>

              <div className="bg-[#0c1017] p-12 rounded-[50px] border border-white/5 shadow-2xl relative overflow-hidden">
                <div className="flex flex-col md:flex-row gap-4 mb-12">
                   <input 
                      type="text" 
                      value={newSourceUrl}
                      onChange={(e) => setNewSourceUrl(e.target.value)}
                      placeholder="رابط قناة يوتيوب، مقال، أو مرجع..."
                      className="flex-1 bg-black/40 border border-white/10 rounded-2xl px-6 py-5 outline-none focus:ring-2 focus:ring-cyan-500 text-white"
                   />
                   <button onClick={() => { if(newSourceUrl) setSources([...sources, {id: Date.now().toString(), type: 'youtube', url: newSourceUrl, title: '', addedAt: Date.now()}]); setNewSourceUrl(''); }} className="bg-white text-black font-black px-10 rounded-2xl hover:bg-slate-200 transition-all">تغذية الوكيل</button>
                </div>

                <div className="space-y-4">
                  <h3 className="text-xs font-bold text-slate-500 uppercase tracking-widest mb-6 px-2">قاعدة المعرفة النشطة</h3>
                  {sources.map(s => (
                    <div key={s.id} className="bg-white/5 p-5 rounded-3xl border border-white/5 flex items-center justify-between group hover:bg-white/10 transition-all">
                       <div className="flex items-center gap-4">
                          <div className="w-12 h-12 bg-red-600/10 text-red-500 rounded-xl flex items-center justify-center text-xl"><i className="fab fa-youtube"></i></div>
                          <p className="text-sm font-bold text-white truncate max-w-[300px]">{s.url}</p>
                       </div>
                       <button className="text-slate-600 hover:text-red-500 opacity-0 group-hover:opacity-100 transition-all p-2"><i className="fas fa-trash"></i></button>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}
        </main>
      </div>

      <footer className="md:hidden bg-[#0c1017] border-t border-white/5 p-4 flex justify-around shadow-2xl">
        {[
          { id: 'create', icon: 'fa-plus-circle' },
          { id: 'agent', icon: 'fa-brain' },
          { id: 'youtube', icon: 'fa-brands fa-youtube' },
          { id: 'library', icon: 'fa-layer-group' }
        ].map(item => (
          <button key={item.id} onClick={() => setActiveTab(item.id as any)} className={`p-3 transition-all ${activeTab === item.id ? 'text-cyan-400 scale-125' : 'text-slate-600'}`}>
            <i className={`fas ${item.icon} text-2xl`}></i>
          </button>
        ))}
      </footer>
    </div>
  );
};

export default App;
