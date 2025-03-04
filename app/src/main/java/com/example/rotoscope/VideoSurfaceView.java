package com.example.rotoscope;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;
import android.view.Surface;

@SuppressLint("ViewConstructor")
public class VideoSurfaceView extends GLSurfaceView {
    //private static final String TAG = "VideoSurfaceView";
    //private static final int SLEEP_TIME_MS = 1000;


    VideoRender mRenderer;
    private MediaPlayer mMediaPlayer = null;

    public VideoSurfaceView(Context context, MediaPlayer mp, String filter) {
        super(context);

        setEGLContextClientVersion(2);
		//Turn on debugging
		setDebugFlags(DEBUG_CHECK_GL_ERROR | DEBUG_LOG_GL_CALLS);

        mMediaPlayer = mp;
        mRenderer = new VideoRender(context, filter, this);
        setEGLConfigChooser(8,8,8,8,0,0); //RGB8UNORM, Depth 0, Stencil 0
        
        setPreserveEGLContextOnPause(true);
        setRenderer(mRenderer);
        //setRenderMode(RENDERMODE_WHEN_DIRTY);
    }

    @Override
    public void onResume() {
        queueEvent(new Runnable(){
                public void run() {
                    mRenderer.setMediaPlayer(mMediaPlayer);
                }});

        super.onResume();
    }
//    public void startTest() throws Exception {
//        Thread.sleep(SLEEP_TIME_MS);
//        mMediaPlayer.start();
//
//        Thread.sleep(SLEEP_TIME_MS * 5);
//        mMediaPlayer.setSurface(null);
//
//        while (mMediaPlayer.isPlaying()) {
//            Thread.sleep(SLEEP_TIME_MS);
//        }
//    }

    public void resetMediaPlayer() throws Exception {
    	//mRenderer.finish(); //finalize the mp4 file
    	mRenderer.resetMediaRender();
    }
    
    private static class VideoRender
        implements GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener{
        private static String TAG = "VideoRender";

        private static final int FLOAT_SIZE_BYTES = 4;
        private static final int TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES;
        private static final int TRIANGLE_VERTICES_DATA_POS_OFFSET = 0;
        private static final int TRIANGLE_VERTICES_DATA_UV_OFFSET = 3;
        private final float[] mTriangleVerticesData = {
            // X, Y, Z, U, V
            -1.0f, -1.0f, 0, 0.f, 0.f,
            1.0f, -1.0f, 0, 1.f, 0.f,
            -1.0f,  1.0f, 0, 0.f, 1.f,
            1.0f,  1.0f, 0, 1.f, 1.f,
        };

        private FloatBuffer mTriangleVertices;

 

        private float[] mMVPMatrix = new float[16];
        private float[] mSTMatrix = new float[16];

        private int mProgram;
        private int mTextureID;
        private int muMVPMatrixHandle;
        private int muSTMatrixHandle;
        private int maPositionHandle;
        private int maTextureHandle;

        private SurfaceTexture mSurface;
        private boolean updateSurface = false;

        private static int GL_TEXTURE_EXTERNAL_OES = 0x8D65;

        private MediaPlayer mMediaPlayer;

        private String m_filter;
        
        private Context m_Context;
        private VideoSurfaceView m_videoViewer;
        
        public VideoRender(Context context, String filter, VideoSurfaceView videoViewer) {
        	m_Context = context;
        	m_filter = filter;
        	m_videoViewer = videoViewer;
            mTriangleVertices = ByteBuffer.allocateDirect(
                mTriangleVerticesData.length * FLOAT_SIZE_BYTES)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
            mTriangleVertices.put(mTriangleVerticesData).position(0);

            Matrix.setIdentityM(mSTMatrix, 0);
            
        }

        public void setMediaPlayer(MediaPlayer player) {
            mMediaPlayer = player;
        }

        public void resetMediaRender() {
        	mMediaPlayer.reset();
        }
        

		public void onDrawFrame(GL10 glUnused) {
        	//Whatever state OpenGL was in, lets make sure we render this to the screen instead of offscreen RT
        	GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        	
        	synchronized(this) {
                if (updateSurface) {
                    mSurface.updateTexImage();
                    mSurface.getTransformMatrix(mSTMatrix);
                    updateSurface = false;               
                }
                else
                	Log.d("DEBUG", "onDrawFrame but not updatedSurface");
                	if (mMediaPlayer.isPlaying() == false) {
                		//Cast the context into parent activity, call finish
                		VideoActivity parentActivity;
                		parentActivity = (VideoActivity)m_Context;
                		parentActivity.finish();
                	}
            }
            
                    
            GLES20.glClearColor(0.0f, 1.0f, 0.0f, 1.0f);
            GLES20.glClear( GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glFlush();
            GLES20.glUseProgram(mProgram);
            checkGlError("glUseProgram");

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, mTextureID);

            mTriangleVertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET);
            GLES20.glVertexAttribPointer(maPositionHandle, 3, GLES20.GL_FLOAT, false,
                TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices);
            checkGlError("glVertexAttribPointer maPosition");
            GLES20.glEnableVertexAttribArray(maPositionHandle);
            checkGlError("glEnableVertexAttribArray maPositionHandle");

            mTriangleVertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET);
            GLES20.glVertexAttribPointer(maTextureHandle, 3, GLES20.GL_FLOAT, false,
                TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices);
            checkGlError("glVertexAttribPointer maTextureHandle");
            GLES20.glEnableVertexAttribArray(maTextureHandle);
            checkGlError("glEnableVertexAttribArray maTextureHandle");

            Matrix.setIdentityM(mMVPMatrix, 0);
            GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mMVPMatrix, 0);
            GLES20.glUniformMatrix4fv(muSTMatrixHandle, 1, false, mSTMatrix, 0);

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            checkGlError("glDrawArrays");
            GLES20.glFinish();
        	      
        }        
                      
        public void onSurfaceChanged(GL10 glUnused, int width, int height) {
        	
        }

        public void onSurfaceCreated(GL10 glUnused, EGLConfig config) {
        	
        	String fragShader = Shader.mOriginalFragmentShader;
        	if (m_filter.equalsIgnoreCase("original")) {
        		fragShader = Shader.mOriginalFragmentShader;
        	}
        	else if (m_filter.equalsIgnoreCase("blacknwhite")) {
        		fragShader = Shader.mGrayScaleFragShader;
        	}
        	else if (m_filter.equalsIgnoreCase("pixellate")){
        		fragShader = Shader.mPixellateFragShader;
        	}
           	else if (m_filter.equalsIgnoreCase("tile")){
        		fragShader = Shader.mTileFragShader;
        	}
           	else if (m_filter.equalsIgnoreCase("posterize")){
        		fragShader = Shader.mPosterizeFragShader;
        	}
           	else if (m_filter.equalsIgnoreCase("laplacian")){
        		fragShader = Shader.laplacianFragShaderCode;
        	}
          	else if (m_filter.equalsIgnoreCase("neon")){
        		fragShader = Shader.neonFragShaderCode;
        	}
           	else if (m_filter.equalsIgnoreCase("kuwahara")){
        		fragShader = Shader.mKuwaharaFragShader;
        	}  
           	else if (m_filter.equalsIgnoreCase("freichen")){
        		fragShader = Shader.freichenFragShaderCode;
        	}
          	else if (m_filter.equalsIgnoreCase("cartoonify")){
        		fragShader = Shader.cartoonifyFragShaderCode;
        	}
           	else if (m_filter.equalsIgnoreCase("vignette")){
        		fragShader = Shader.mVignetteFragShader;
        	}
          	else if (m_filter.equalsIgnoreCase("sepia")){
        		fragShader = Shader.mSepiaFragShader;
        	}
          	else if (m_filter.equalsIgnoreCase("savetofile")){
        		fragShader = Shader.msavetofileFragShader;
        	}
        	mProgram = createProgram(Shader.mVertexShader, fragShader);
            if (mProgram == 0) {
            	throw new RuntimeException("createProgram returned 0");
                //return;
            }
            maPositionHandle = GLES20.glGetAttribLocation(mProgram, "aPosition");
            checkGlError("glGetAttribLocation aPosition");
            if (maPositionHandle == -1) {
                throw new RuntimeException("Could not get attrib location for aPosition");
            }
            maTextureHandle = GLES20.glGetAttribLocation(mProgram, "aTextureCoord");
            checkGlError("glGetAttribLocation aTextureCoord");
            if (maTextureHandle == -1) {
                throw new RuntimeException("Could not get attrib location for aTextureCoord");
            }

            muMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
            checkGlError("glGetUniformLocation uMVPMatrix");
            if (muMVPMatrixHandle == -1) {
                throw new RuntimeException("Could not get attrib location for uMVPMatrix");
            }

            muSTMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uSTMatrix");
            checkGlError("glGetUniformLocation uSTMatrix");
            if (muSTMatrixHandle == -1) {
                throw new RuntimeException("Could not get attrib location for uSTMatrix");
            }


            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);

            mTextureID = textures[0];
            GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, mTextureID);
            checkGlError("glBindTexture mTextureID");

            GLES20.glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
                                   GLES20.GL_NEAREST);
            GLES20.glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
                                   GLES20.GL_LINEAR);

            /*
             * Create the SurfaceTexture that will feed this textureID,
             * and pass it to the MediaPlayer
             */
            mSurface = new SurfaceTexture(mTextureID);
            mSurface.setOnFrameAvailableListener(this);
            //mMediaPlayer.setOnPreparedListener(this);

           	Surface surface = new Surface(mSurface); 
           	
           	mMediaPlayer.setSurface(surface);
            surface.release();


            //Now start the media player
            try {
                mMediaPlayer.prepare(); //or prepare

            } catch (Exception t) {
                Log.e(TAG, "media player prepare failed");
                t.printStackTrace();
            }

            synchronized(this) {
                updateSurface = false;
            }
            System.out.println("about to start");
            mMediaPlayer.start();
        }

        synchronized public void onFrameAvailable(SurfaceTexture surface) {
            updateSurface = true;
        }

        private int loadShader(int shaderType, String source) {
            int shader = GLES20.glCreateShader(shaderType);
            if (shader != 0) {
                GLES20.glShaderSource(shader, source);
                GLES20.glCompileShader(shader);
                int[] compiled = new int[1];
                GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
                if (compiled[0] == 0) {
                    Log.e(TAG, "Could not compile shader " + shaderType + ":");
                    Log.e(TAG, GLES20.glGetShaderInfoLog(shader));
                    GLES20.glDeleteShader(shader);
                    shader = 0;
                }
            }
            return shader;
        }

        private int createProgram(String vertexSource, String fragmentSource) {
            int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
            if (vertexShader == 0) {
                return 0;
            }
            int pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
            if (pixelShader == 0) {
                return 0;
            }

            int program = GLES20.glCreateProgram();
            if (program != 0) {
                GLES20.glAttachShader(program, vertexShader);
                checkGlError("glAttachShader");
                GLES20.glAttachShader(program, pixelShader);
                checkGlError("glAttachShader");
                GLES20.glLinkProgram(program);
                int[] linkStatus = new int[1];
                GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
                if (linkStatus[0] != GLES20.GL_TRUE) {
                    Log.e(TAG, "Could not link program: ");
                    Log.e(TAG, GLES20.glGetProgramInfoLog(program));
                    GLES20.glDeleteProgram(program);
                    program = 0;
                }
            }
            return program;
        } 

        private void checkGlError(String op) {
            int error;
            while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
                Log.e(TAG, op + ": glError " + error);
                throw new RuntimeException(op + ": glError " + error);
            }
        }

//		@Override
//		public void onPrepared(MediaPlayer mp) {
//			mp.start();
//			
//		}

    }  // End of class VideoRender.

}  // End of class VideoSurfaceView.