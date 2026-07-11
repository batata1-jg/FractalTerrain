package me.batata_1.fractal_terrain.noise;

import me.batata_1.fractal_terrain.math.Vector2;
import me.batata_1.fractal_terrain.math.Vector3;
import me.batata_1.fractal_terrain.noise.strategy.BasicGridWarpStrategy;
import me.batata_1.fractal_terrain.noise.strategy.CellularStrategy;
import me.batata_1.fractal_terrain.noise.strategy.NoiseTables;
import me.batata_1.fractal_terrain.noise.strategy.OpenSimplex2SStrategy;
import me.batata_1.fractal_terrain.noise.strategy.OpenSimplex2Strategy;
import me.batata_1.fractal_terrain.noise.strategy.PerlinStrategy;
import me.batata_1.fractal_terrain.noise.strategy.SimplexGradientWarpStrategy;
import me.batata_1.fractal_terrain.noise.strategy.ValueCubicStrategy;
import me.batata_1.fractal_terrain.noise.strategy.ValueStrategy;

// MIT License
//
// Copyright(c) 2023 Jordan Peck (jordan.me2@gmail.com)
// Copyright(c) 2023 Contributors
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files(the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and / or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions :
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
//
// .'',;:cldxkO00KKXXNNWWWNNXKOkxdollcc::::::;:::ccllloooolllllllllooollc:,'...
// ...........',;cldxkO000Okxdlc::;;;,,;;;::cclllllll
// ..',;:ldxO0KXXNNNNNNNNXXK0kxdolcc::::::;;;,,,,,,;;;;;;;;;;:::cclllllc:;'....
// ...........',;:ldxO0KXXXK0Okxdolc::;;;;::cllodddddo
// ...',:loxO0KXNNNNNXXKK0Okxdolc::;::::::::;;;,,'''''.....''',;:clllllc:;,'............''''''''',;:loxO0KXNNNNNXK0Okxdollccccllodxxxxxxd
// ....';:ldkO0KXXXKK00Okxdolcc:;;;;;::cclllcc:;;,''.....
// ....',;clooddolcc:;;;;,,;;;;;::::;;;;;;:cloxk0KXNWWWWWWNXKK0Okxddoooddxxkkkkkxx
// .....';:ldxkOOOOOkxxdolcc:;;;,,,;;:cllooooolcc:;'...
// ..,:codxkkkxddooollloooooooollcc:::::clodkO0KXNWWWWWWNNXK00Okxxxxxxxxkkkkxxx
// . ....';:cloddddo___________,,,,;;:clooddddoolc:,...
// ..,:ldx__00OOOkkk___kkkkkkxxdollc::::cclodkO0KXXNNNNNNXXK0OOkxxxxxxxxxxxxddd
// .......',;:cccc:|           |,,,;;:cclooddddoll:;'..     ..';cox|  \KKK000|
// |KK00OOkxdocc___;::clldxxkO0KKKKK00Okkxdddddddddddddddoo
// .......'',,,,,''|   ________|',,;;::cclloooooolc:;'......___:ldk|   \KK000|   |XKKK0Okxolc|
// |;;::cclodxxkkkkxxdoolllcclllooodddooooo
// ''......''''....|   |  ....'',,,,;;;::cclloooollc:;,''.'|   |oxk|    \OOO0|
// |KKK00Oxdoll|___|;;;;;::ccllllllcc::;;,,;;;:cclloooooooo
// ;;,''.......... |   |_____',,;;;____:___cllo________.___|   |___|     \xkk|
// |KK_______ool___:::;________;;;_______...'',;;:ccclllloo
// c:;,''......... |         |:::/     '   |lo/        |           |      \dx|   |0/       \d|   |cc/        |'/
// \......',,;;:ccllo
// ol:;,'..........|    _____|ll/    __    |o/   ______|____    ___|   |   \o|   |/   ___   \|   |o/   ______|/   ___
// \ .......'',;:clo
// dlc;,...........|   |::clooo|    /  |   |x\___   \KXKKK0|   |dol|   |\   \|   |   |   |   |   |d\___   \..|   |  /
// /       ....',:cl
// xoc;'...  .....'|   |llodddd|    \__|   |_____\   \KKK0O|   |lc:|   |'\       |   |___|   |   |_____\   \.|
// |_/___/...      ...',;:c
// dlc;'... ....',;|   |oddddddo\          |          |Okkx|   |::;|   |..\      |\         /|   |          | \
// |...    ....',;:c
// ol:,'.......',:c|___|xxxddollc\_____,___|_________/ddoll|___|,,,|___|...\_____|:\
// ______/l|___|_________/...\________|'........',;::cc
// c:;'.......';:codxxkkkkxxolc::;::clodxkOO0OOkkxdollc::;;,,''''',,,,''''''''''',,'''''',;:loxkkOOkxol:;,'''',,;:ccllcc:;,'''''',;::ccll
// ;,'.......',:codxkOO0OOkxdlc:;,,;;:cldxxkkxxdolc:;;,,''.....'',;;:::;;,,,'''''........,;cldkO0KK0Okdoc::;;::cloodddoolc:;;;;;::ccllooo
// .........',;:lodxOO0000Okdoc:,,',,;:clloddoolc:;,''.......'',;:clooollc:;;,,''.......',:ldkOKXNNXX0Oxdolllloddxxxxxxdolccccccllooodddd
// .
// .....';:cldxkO0000Okxol:;,''',,;::cccc:;,,'.......'',;:cldxxkkxxdolc:;;,'.......';coxOKXNWWWNXKOkxddddxxkkkkkkxdoollllooddxxxxkkk
//
// ....',;:codxkO000OOxdoc:;,''',,,;;;;,''.......',,;:clodkO00000Okxolc::;,,''..',;:ldxOKXNWWWNNK0OkkkkkkkkkkkxxddooooodxxkOOOOO000
//
// ....',;;clodxkkOOOkkdolc:;,,,,,,,,'..........,;:clodxkO0KKXKK0Okxdolcc::;;,,,;;:codkO0XXNNNNXKK0OOOOOkkkkxxdoollloodxkO0KKKXXXXX
//
// VERSION: 1.1.1
// https://github.com/Auburn/FastNoiseLite

// To switch between using floats or doubles for input position,
// perform a file-wide replace on the following strings (including /*FNLfloat*/)
// /*FNLfloat*/ float
// /*FNLfloat*/ double

public class FastNoiseLite {
    public enum NoiseType {
        OpenSimplex2,
        OpenSimplex2S,
        Cellular,
        Perlin,
        ValueCubic,
        Value
    };

    public enum RotationType3D {
        None,
        ImproveXYPlanes,
        ImproveXZPlanes
    };

    public enum FractalType {
        None,
        FBm,
        Ridged,
        PingPong,
        DomainWarpProgressive,
        DomainWarpIndependent
    };

    public enum CellularDistanceFunction {
        Euclidean,
        EuclideanSq,
        Manhattan,
        Hybrid
    };

    public enum CellularReturnType {
        CellValue,
        Distance,
        Distance2,
        Distance2Add,
        Distance2Sub,
        Distance2Mul,
        Distance2Div
    };

    public enum DomainWarpType {
        OpenSimplex2,
        OpenSimplex2Reduced,
        BasicGrid
    };

    private enum TransformType3D {
        None,
        ImproveXYPlanes,
        ImproveXZPlanes,
        DefaultOpenSimplex2
    };

    private int mSeed = 1337;
    private float mFrequency = 0.01f;
    private NoiseType mNoiseType = NoiseType.OpenSimplex2;
    private RotationType3D mRotationType3D = RotationType3D.None;
    private TransformType3D mTransformType3D = TransformType3D.DefaultOpenSimplex2;

    private FractalType mFractalType = FractalType.None;
    private int mOctaves = 3;
    private float mLacunarity = 2.0f;
    private float mGain = 0.5f;
    private float mWeightedStrength = 0.0f;
    private float mPingPongStrength = 2.0f;

    private float mFractalBounding = 1 / 1.75f;

    private CellularDistanceFunction mCellularDistanceFunction = CellularDistanceFunction.EuclideanSq;
    private CellularReturnType mCellularReturnType = CellularReturnType.Distance;
    private float mCellularJitterModifier = 1.0f;

    private DomainWarpType mDomainWarpType = DomainWarpType.OpenSimplex2;
    private TransformType3D mWarpTransformType3D = TransformType3D.DefaultOpenSimplex2;
    private float mDomainWarpAmp = 1.0f;

    /// <summary>
    /// Create new FastNoise object with default seed
    /// </summary>
    public FastNoiseLite() {}

    /// <summary>
    /// Create new FastNoise object with specified seed
    /// </summary>
    public FastNoiseLite(int seed) {
        SetSeed(seed);
    }

    /// <summary>
    /// Sets seed used for all noise types
    /// </summary>
    /// <remarks>
    /// Default: 1337
    /// </remarks>
    public void SetSeed(int seed) {
        mSeed = seed;
    }

    /// <summary>
    /// Sets frequency for all noise types
    /// </summary>
    /// <remarks>
    /// Default: 0.01
    /// </remarks>
    public void SetFrequency(float frequency) {
        mFrequency = frequency;
    }

    /// <summary>
    /// Sets noise algorithm used for GetNoise(...)
    /// </summary>
    /// <remarks>
    /// Default: OpenSimplex2
    /// </remarks>
    public void SetNoiseType(NoiseType noiseType) {
        mNoiseType = noiseType;
        UpdateTransformType3D();
    }

    /// <summary>
    /// Sets domain rotation type for 3D Noise and 3D DomainWarp.
    /// Can aid in reducing directional artifacts when sampling a 2D plane in 3D
    /// </summary>
    /// <remarks>
    /// Default: None
    /// </remarks>
    public void SetRotationType3D(RotationType3D rotationType3D) {
        mRotationType3D = rotationType3D;
        UpdateTransformType3D();
        UpdateWarpTransformType3D();
    }

    /// <summary>
    /// Sets method for combining octaves in all fractal noise types
    /// </summary>
    /// <remarks>
    /// Default: None
    /// Note: FractalType.DomainWarp... only affects DomainWarp(...)
    /// </remarks>
    public void SetFractalType(FractalType fractalType) {
        mFractalType = fractalType;
    }

    /// <summary>
    /// Sets octave count for all fractal noise types
    /// </summary>
    /// <remarks>
    /// Default: 3
    /// </remarks>
    public void SetFractalOctaves(int octaves) {
        mOctaves = octaves;
        CalculateFractalBounding();
    }

    /// <summary>
    /// Sets octave lacunarity for all fractal noise types
    /// </summary>
    /// <remarks>
    /// Default: 2.0
    /// </remarks>
    public void SetFractalLacunarity(float lacunarity) {
        mLacunarity = lacunarity;
    }

    /// <summary>
    /// Sets octave gain for all fractal noise types
    /// </summary>
    /// <remarks>
    /// Default: 0.5
    /// </remarks>
    public void SetFractalGain(float gain) {
        mGain = gain;
        CalculateFractalBounding();
    }

    /// <summary>
    /// Sets octave weighting for all none DomainWarp fratal types
    /// </summary>
    /// <remarks>
    /// Default: 0.0
    /// Note: Keep between 0...1 to maintain -1...1 output bounding
    /// </remarks>
    public void SetFractalWeightedStrength(float weightedStrength) {
        mWeightedStrength = weightedStrength;
    }

    /// <summary>
    /// Sets strength of the fractal ping pong effect
    /// </summary>
    /// <remarks>
    /// Default: 2.0
    /// </remarks>
    public void SetFractalPingPongStrength(float pingPongStrength) {
        mPingPongStrength = pingPongStrength;
    }

    /// <summary>
    /// Sets distance function used in cellular noise calculations
    /// </summary>
    /// <remarks>
    /// Default: Distance
    /// </remarks>
    public void SetCellularDistanceFunction(CellularDistanceFunction cellularDistanceFunction) {
        mCellularDistanceFunction = cellularDistanceFunction;
    }

    /// <summary>
    /// Sets return type from cellular noise calculations
    /// </summary>
    /// <remarks>
    /// Default: EuclideanSq
    /// </remarks>
    public void SetCellularReturnType(CellularReturnType cellularReturnType) {
        mCellularReturnType = cellularReturnType;
    }

    /// <summary>
    /// Sets the maximum distance a cellular point can move from it's grid position
    /// </summary>
    /// <remarks>
    /// Default: 1.0
    /// Note: Setting this higher than 1 will cause artifacts
    /// </remarks>
    public void SetCellularJitter(float cellularJitter) {
        mCellularJitterModifier = cellularJitter;
    }

    /// <summary>
    /// Sets the warp algorithm when using DomainWarp(...)
    /// </summary>
    /// <remarks>
    /// Default: OpenSimplex2
    /// </remarks>
    public void SetDomainWarpType(DomainWarpType domainWarpType) {
        mDomainWarpType = domainWarpType;
        UpdateWarpTransformType3D();
    }

    /// <summary>
    /// Sets the maximum warp distance from original position when using DomainWarp(...)
    /// </summary>
    /// <remarks>
    /// Default: 1.0
    /// </remarks>
    public void SetDomainWarpAmp(float domainWarpAmp) {
        mDomainWarpAmp = domainWarpAmp;
    }

    /// <summary>
    /// 2D noise at given position using current settings
    /// </summary>
    /// <returns>
    /// Noise output bounded between -1...1
    /// </returns>
    public float GetNoise(/*FNLfloat*/ float x, /*FNLfloat*/ float y) {
        x *= mFrequency;
        y *= mFrequency;

        switch (mNoiseType) {
            case OpenSimplex2:
            case OpenSimplex2S:
                {
                    final /*FNLfloat*/ float SQRT3 = (/*FNLfloat*/ float) 1.7320508075688772935274463415059;
                    final /*FNLfloat*/ float F2 = 0.5f * (SQRT3 - 1);
                    /*FNLfloat*/ float t = (x + y) * F2;
                    x += t;
                    y += t;
                }
                break;
            default:
                break;
        }

        switch (mFractalType) {
            default:
                return GenNoiseSingle(mSeed, x, y);
            case FBm:
                return GenFractalFBm(x, y);
            case Ridged:
                return GenFractalRidged(x, y);
            case PingPong:
                return GenFractalPingPong(x, y);
        }
    }

    /// <summary>
    /// 3D noise at given position using current settings
    /// </summary>
    /// <returns>
    /// Noise output bounded between -1...1
    /// </returns>
    public float GetNoise(/*FNLfloat*/ float x, /*FNLfloat*/ float y, /*FNLfloat*/ float z) {
        x *= mFrequency;
        y *= mFrequency;
        z *= mFrequency;

        switch (mTransformType3D) {
            case ImproveXYPlanes:
                {
                    /*FNLfloat*/ float xy = x + y;
                    /*FNLfloat*/ float s2 = xy * -(/*FNLfloat*/ float) 0.211324865405187;
                    z *= (/*FNLfloat*/ float) 0.577350269189626;
                    x += s2 - z;
                    y = y + s2 - z;
                    z += xy * (/*FNLfloat*/ float) 0.577350269189626;
                }
                break;
            case ImproveXZPlanes:
                {
                    /*FNLfloat*/ float xz = x + z;
                    /*FNLfloat*/ float s2 = xz * -(/*FNLfloat*/ float) 0.211324865405187;
                    y *= (/*FNLfloat*/ float) 0.577350269189626;
                    x += s2 - y;
                    z += s2 - y;
                    y += xz * (/*FNLfloat*/ float) 0.577350269189626;
                }
                break;
            case DefaultOpenSimplex2:
                {
                    final /*FNLfloat*/ float R3 = (/*FNLfloat*/ float) (2.0 / 3.0);
                    /*FNLfloat*/ float r = (x + y + z) * R3; // Rotation, not skew
                    x = r - x;
                    y = r - y;
                    z = r - z;
                }
                break;
            default:
                break;
        }

        switch (mFractalType) {
            default:
                return GenNoiseSingle(mSeed, x, y, z);
            case FBm:
                return GenFractalFBm(x, y, z);
            case Ridged:
                return GenFractalRidged(x, y, z);
            case PingPong:
                return GenFractalPingPong(x, y, z);
        }
    }

    /// <summary>
    /// 2D warps the input position using current domain warp settings
    /// </summary>
    /// <example>
    /// Example usage with GetNoise
    /// <code>DomainWarp(coord)
    /// noise = GetNoise(x, y)</code>
    /// </example>
    public void DomainWarp(Vector2 coord) {
        switch (mFractalType) {
            default:
                DomainWarpSingle(coord);
                break;
            case DomainWarpProgressive:
                DomainWarpFractalProgressive(coord);
                break;
            case DomainWarpIndependent:
                DomainWarpFractalIndependent(coord);
                break;
        }
    }

    /// <summary>
    /// 3D warps the input position using current domain warp settings
    /// </summary>
    /// <example>
    /// Example usage with GetNoise
    /// <code>DomainWarp(coord)
    /// noise = GetNoise(x, y, z)</code>
    /// </example>
    public void DomainWarp(Vector3 coord) {
        switch (mFractalType) {
            default:
                DomainWarpSingle(coord);
                break;
            case DomainWarpProgressive:
                DomainWarpFractalProgressive(coord);
                break;
            case DomainWarpIndependent:
                DomainWarpFractalIndependent(coord);
                break;
        }
    }

    private void CalculateFractalBounding() {
        float gain = NoiseTables.FastAbs(mGain);
        float amp = gain;
        float ampFractal = 1.0f;
        for (int i = 1; i < mOctaves; i++) {
            ampFractal += amp;
            amp *= gain;
        }
        mFractalBounding = 1 / ampFractal;
    }

    // Generic noise gen

    private float GenNoiseSingle(int seed, /*FNLfloat*/ float x, /*FNLfloat*/ float y) {
        switch (mNoiseType) {
            case OpenSimplex2:
                return OpenSimplex2Strategy.SingleSimplex(seed, x, y);
            case OpenSimplex2S:
                return OpenSimplex2SStrategy.SingleOpenSimplex2S(seed, x, y);
            case Cellular:
                return CellularStrategy.SingleCellular(
                        seed, x, y, mCellularDistanceFunction, mCellularReturnType, mCellularJitterModifier);
            case Perlin:
                return PerlinStrategy.SinglePerlin(seed, x, y);
            case ValueCubic:
                return ValueCubicStrategy.SingleValueCubic(seed, x, y);
            case Value:
                return ValueStrategy.SingleValue(seed, x, y);
            default:
                return 0;
        }
    }

    private float GenNoiseSingle(int seed, /*FNLfloat*/ float x, /*FNLfloat*/ float y, /*FNLfloat*/ float z) {
        switch (mNoiseType) {
            case OpenSimplex2:
                return OpenSimplex2Strategy.SingleOpenSimplex2(seed, x, y, z);
            case OpenSimplex2S:
                return OpenSimplex2SStrategy.SingleOpenSimplex2S(seed, x, y, z);
            case Cellular:
                return CellularStrategy.SingleCellular(
                        seed, x, y, z, mCellularDistanceFunction, mCellularReturnType, mCellularJitterModifier);
            case Perlin:
                return PerlinStrategy.SinglePerlin(seed, x, y, z);
            case ValueCubic:
                return ValueCubicStrategy.SingleValueCubic(seed, x, y, z);
            case Value:
                return ValueStrategy.SingleValue(seed, x, y, z);
            default:
                return 0;
        }
    }

    // Noise Coordinate Transforms (frequency, and possible skew or rotation)

    private void UpdateTransformType3D() {
        switch (mRotationType3D) {
            case ImproveXYPlanes:
                mTransformType3D = TransformType3D.ImproveXYPlanes;
                break;
            case ImproveXZPlanes:
                mTransformType3D = TransformType3D.ImproveXZPlanes;
                break;
            default:
                switch (mNoiseType) {
                    case OpenSimplex2:
                    case OpenSimplex2S:
                        mTransformType3D = TransformType3D.DefaultOpenSimplex2;
                        break;
                    default:
                        mTransformType3D = TransformType3D.None;
                        break;
                }
                break;
        }
    }

    private void UpdateWarpTransformType3D() {
        switch (mRotationType3D) {
            case ImproveXYPlanes:
                mWarpTransformType3D = TransformType3D.ImproveXYPlanes;
                break;
            case ImproveXZPlanes:
                mWarpTransformType3D = TransformType3D.ImproveXZPlanes;
                break;
            default:
                switch (mDomainWarpType) {
                    case OpenSimplex2:
                    case OpenSimplex2Reduced:
                        mWarpTransformType3D = TransformType3D.DefaultOpenSimplex2;
                        break;
                    default:
                        mWarpTransformType3D = TransformType3D.None;
                        break;
                }
                break;
        }
    }

    // Fractal FBm

    private float GenFractalFBm(/*FNLfloat*/ float x, /*FNLfloat*/ float y) {
        int seed = mSeed;
        float sum = 0;
        float amp = mFractalBounding;

        for (int i = 0; i < mOctaves; i++) {
            float noise = GenNoiseSingle(seed++, x, y);
            sum += noise * amp;
            amp *= NoiseTables.Lerp(1.0f, NoiseTables.FastMin(noise + 1, 2) * 0.5f, mWeightedStrength);

            x *= mLacunarity;
            y *= mLacunarity;
            amp *= mGain;
        }

        return sum;
    }

    private float GenFractalFBm(/*FNLfloat*/ float x, /*FNLfloat*/ float y, /*FNLfloat*/ float z) {
        int seed = mSeed;
        float sum = 0;
        float amp = mFractalBounding;

        for (int i = 0; i < mOctaves; i++) {
            float noise = GenNoiseSingle(seed++, x, y, z);
            sum += noise * amp;
            amp *= NoiseTables.Lerp(1.0f, (noise + 1) * 0.5f, mWeightedStrength);

            x *= mLacunarity;
            y *= mLacunarity;
            z *= mLacunarity;
            amp *= mGain;
        }

        return sum;
    }

    // Fractal Ridged

    private float GenFractalRidged(/*FNLfloat*/ float x, /*FNLfloat*/ float y) {
        int seed = mSeed;
        float sum = 0;
        float amp = mFractalBounding;

        for (int i = 0; i < mOctaves; i++) {
            float noise = NoiseTables.FastAbs(GenNoiseSingle(seed++, x, y));
            sum += (noise * -2 + 1) * amp;
            amp *= NoiseTables.Lerp(1.0f, 1 - noise, mWeightedStrength);

            x *= mLacunarity;
            y *= mLacunarity;
            amp *= mGain;
        }

        return sum;
    }

    private float GenFractalRidged(/*FNLfloat*/ float x, /*FNLfloat*/ float y, /*FNLfloat*/ float z) {
        int seed = mSeed;
        float sum = 0;
        float amp = mFractalBounding;

        for (int i = 0; i < mOctaves; i++) {
            float noise = NoiseTables.FastAbs(GenNoiseSingle(seed++, x, y, z));
            sum += (noise * -2 + 1) * amp;
            amp *= NoiseTables.Lerp(1.0f, 1 - noise, mWeightedStrength);

            x *= mLacunarity;
            y *= mLacunarity;
            z *= mLacunarity;
            amp *= mGain;
        }

        return sum;
    }

    // Fractal PingPong

    private float GenFractalPingPong(/*FNLfloat*/ float x, /*FNLfloat*/ float y) {
        int seed = mSeed;
        float sum = 0;
        float amp = mFractalBounding;

        for (int i = 0; i < mOctaves; i++) {
            float noise = NoiseTables.PingPong((GenNoiseSingle(seed++, x, y) + 1) * mPingPongStrength);
            sum += (noise - 0.5f) * 2 * amp;
            amp *= NoiseTables.Lerp(1.0f, noise, mWeightedStrength);

            x *= mLacunarity;
            y *= mLacunarity;
            amp *= mGain;
        }

        return sum;
    }

    private float GenFractalPingPong(/*FNLfloat*/ float x, /*FNLfloat*/ float y, /*FNLfloat*/ float z) {
        int seed = mSeed;
        float sum = 0;
        float amp = mFractalBounding;

        for (int i = 0; i < mOctaves; i++) {
            float noise = NoiseTables.PingPong((GenNoiseSingle(seed++, x, y, z) + 1) * mPingPongStrength);
            sum += (noise - 0.5f) * 2 * amp;
            amp *= NoiseTables.Lerp(1.0f, noise, mWeightedStrength);

            x *= mLacunarity;
            y *= mLacunarity;
            z *= mLacunarity;
            amp *= mGain;
        }

        return sum;
    }

    // Domain Warp

    private void DoSingleDomainWarp(
            int seed, float amp, float freq, /*FNLfloat*/ float x, /*FNLfloat*/ float y, Vector2 coord) {
        switch (mDomainWarpType) {
            case OpenSimplex2:
                SimplexGradientWarpStrategy.SingleDomainWarpSimplexGradient(
                        seed, amp * 38.283687591552734375f, freq, x, y, coord, false);
                break;
            case OpenSimplex2Reduced:
                SimplexGradientWarpStrategy.SingleDomainWarpSimplexGradient(seed, amp * 16.0f, freq, x, y, coord, true);
                break;
            case BasicGrid:
                BasicGridWarpStrategy.SingleDomainWarpBasicGrid(seed, amp, freq, x, y, coord);
                break;
        }
    }

    private void DoSingleDomainWarp(
            int seed,
            float amp,
            float freq, /*FNLfloat*/
            float x, /*FNLfloat*/
            float y, /*FNLfloat*/
            float z,
            Vector3 coord) {
        switch (mDomainWarpType) {
            case OpenSimplex2:
                SimplexGradientWarpStrategy.SingleDomainWarpOpenSimplex2Gradient(
                        seed, amp * 32.69428253173828125f, freq, x, y, z, coord, false);
                break;
            case OpenSimplex2Reduced:
                SimplexGradientWarpStrategy.SingleDomainWarpOpenSimplex2Gradient(
                        seed, amp * 7.71604938271605f, freq, x, y, z, coord, true);
                break;
            case BasicGrid:
                BasicGridWarpStrategy.SingleDomainWarpBasicGrid(seed, amp, freq, x, y, z, coord);
                break;
        }
    }

    // Domain Warp Single Wrapper

    private void DomainWarpSingle(Vector2 coord) {
        int seed = mSeed;
        float amp = mDomainWarpAmp * mFractalBounding;
        float freq = mFrequency;

        /*FNLfloat*/ float xs = coord.x;
        /*FNLfloat*/ float ys = coord.y;
        switch (mDomainWarpType) {
            case OpenSimplex2:
            case OpenSimplex2Reduced:
                {
                    final /*FNLfloat*/ float SQRT3 = (/*FNLfloat*/ float) 1.7320508075688772935274463415059;
                    final /*FNLfloat*/ float F2 = 0.5f * (SQRT3 - 1);
                    /*FNLfloat*/ float t = (xs + ys) * F2;
                    xs += t;
                    ys += t;
                }
                break;
            default:
                break;
        }

        DoSingleDomainWarp(seed, amp, freq, xs, ys, coord);
    }

    private void DomainWarpSingle(Vector3 coord) {
        int seed = mSeed;
        float amp = mDomainWarpAmp * mFractalBounding;
        float freq = mFrequency;

        /*FNLfloat*/ float xs = coord.x;
        /*FNLfloat*/ float ys = coord.y;
        /*FNLfloat*/ float zs = coord.z;
        switch (mWarpTransformType3D) {
            case ImproveXYPlanes:
                {
                    /*FNLfloat*/ float xy = xs + ys;
                    /*FNLfloat*/ float s2 = xy * -(/*FNLfloat*/ float) 0.211324865405187;
                    zs *= (/*FNLfloat*/ float) 0.577350269189626;
                    xs += s2 - zs;
                    ys = ys + s2 - zs;
                    zs += xy * (/*FNLfloat*/ float) 0.577350269189626;
                }
                break;
            case ImproveXZPlanes:
                {
                    /*FNLfloat*/ float xz = xs + zs;
                    /*FNLfloat*/ float s2 = xz * -(/*FNLfloat*/ float) 0.211324865405187;
                    ys *= (/*FNLfloat*/ float) 0.577350269189626;
                    xs += s2 - ys;
                    zs += s2 - ys;
                    ys += xz * (/*FNLfloat*/ float) 0.577350269189626;
                }
                break;
            case DefaultOpenSimplex2:
                {
                    final /*FNLfloat*/ float R3 = (/*FNLfloat*/ float) (2.0 / 3.0);
                    /*FNLfloat*/ float r = (xs + ys + zs) * R3; // Rotation, not skew
                    xs = r - xs;
                    ys = r - ys;
                    zs = r - zs;
                }
                break;
            default:
                break;
        }

        DoSingleDomainWarp(seed, amp, freq, xs, ys, zs, coord);
    }

    // Domain Warp Fractal Progressive

    private void DomainWarpFractalProgressive(Vector2 coord) {
        int seed = mSeed;
        float amp = mDomainWarpAmp * mFractalBounding;
        float freq = mFrequency;

        for (int i = 0; i < mOctaves; i++) {
            /*FNLfloat*/ float xs = coord.x;
            /*FNLfloat*/ float ys = coord.y;
            switch (mDomainWarpType) {
                case OpenSimplex2:
                case OpenSimplex2Reduced:
                    {
                        final /*FNLfloat*/ float SQRT3 = (/*FNLfloat*/ float) 1.7320508075688772935274463415059;
                        final /*FNLfloat*/ float F2 = 0.5f * (SQRT3 - 1);
                        /*FNLfloat*/ float t = (xs + ys) * F2;
                        xs += t;
                        ys += t;
                    }
                    break;
                default:
                    break;
            }

            DoSingleDomainWarp(seed, amp, freq, xs, ys, coord);

            seed++;
            amp *= mGain;
            freq *= mLacunarity;
        }
    }

    private void DomainWarpFractalProgressive(Vector3 coord) {
        int seed = mSeed;
        float amp = mDomainWarpAmp * mFractalBounding;
        float freq = mFrequency;

        for (int i = 0; i < mOctaves; i++) {
            /*FNLfloat*/ float xs = coord.x;
            /*FNLfloat*/ float ys = coord.y;
            /*FNLfloat*/ float zs = coord.z;
            switch (mWarpTransformType3D) {
                case ImproveXYPlanes:
                    {
                        /*FNLfloat*/ float xy = xs + ys;
                        /*FNLfloat*/ float s2 = xy * -(/*FNLfloat*/ float) 0.211324865405187;
                        zs *= (/*FNLfloat*/ float) 0.577350269189626;
                        xs += s2 - zs;
                        ys = ys + s2 - zs;
                        zs += xy * (/*FNLfloat*/ float) 0.577350269189626;
                    }
                    break;
                case ImproveXZPlanes:
                    {
                        /*FNLfloat*/ float xz = xs + zs;
                        /*FNLfloat*/ float s2 = xz * -(/*FNLfloat*/ float) 0.211324865405187;
                        ys *= (/*FNLfloat*/ float) 0.577350269189626;
                        xs += s2 - ys;
                        zs += s2 - ys;
                        ys += xz * (/*FNLfloat*/ float) 0.577350269189626;
                    }
                    break;
                case DefaultOpenSimplex2:
                    {
                        final /*FNLfloat*/ float R3 = (/*FNLfloat*/ float) (2.0 / 3.0);
                        /*FNLfloat*/ float r = (xs + ys + zs) * R3; // Rotation, not skew
                        xs = r - xs;
                        ys = r - ys;
                        zs = r - zs;
                    }
                    break;
                default:
                    break;
            }

            DoSingleDomainWarp(seed, amp, freq, xs, ys, zs, coord);

            seed++;
            amp *= mGain;
            freq *= mLacunarity;
        }
    }

    // Domain Warp Fractal Independant
    private void DomainWarpFractalIndependent(Vector2 coord) {
        /*FNLfloat*/ float xs = coord.x;
        /*FNLfloat*/ float ys = coord.y;
        switch (mDomainWarpType) {
            case OpenSimplex2:
            case OpenSimplex2Reduced:
                {
                    final /*FNLfloat*/ float SQRT3 = (/*FNLfloat*/ float) 1.7320508075688772935274463415059;
                    final /*FNLfloat*/ float F2 = 0.5f * (SQRT3 - 1);
                    /*FNLfloat*/ float t = (xs + ys) * F2;
                    xs += t;
                    ys += t;
                }
                break;
            default:
                break;
        }

        int seed = mSeed;
        float amp = mDomainWarpAmp * mFractalBounding;
        float freq = mFrequency;

        for (int i = 0; i < mOctaves; i++) {
            DoSingleDomainWarp(seed, amp, freq, xs, ys, coord);

            seed++;
            amp *= mGain;
            freq *= mLacunarity;
        }
    }

    private void DomainWarpFractalIndependent(Vector3 coord) {
        /*FNLfloat*/ float xs = coord.x;
        /*FNLfloat*/ float ys = coord.y;
        /*FNLfloat*/ float zs = coord.z;
        switch (mWarpTransformType3D) {
            case ImproveXYPlanes:
                {
                    /*FNLfloat*/ float xy = xs + ys;
                    /*FNLfloat*/ float s2 = xy * -(/*FNLfloat*/ float) 0.211324865405187;
                    zs *= (/*FNLfloat*/ float) 0.577350269189626;
                    xs += s2 - zs;
                    ys = ys + s2 - zs;
                    zs += xy * (/*FNLfloat*/ float) 0.577350269189626;
                }
                break;
            case ImproveXZPlanes:
                {
                    /*FNLfloat*/ float xz = xs + zs;
                    /*FNLfloat*/ float s2 = xz * -(/*FNLfloat*/ float) 0.211324865405187;
                    ys *= (/*FNLfloat*/ float) 0.577350269189626;
                    xs += s2 - ys;
                    zs += s2 - ys;
                    ys += xz * (/*FNLfloat*/ float) 0.577350269189626;
                }
                break;
            case DefaultOpenSimplex2:
                {
                    final /*FNLfloat*/ float R3 = (/*FNLfloat*/ float) (2.0 / 3.0);
                    /*FNLfloat*/ float r = (xs + ys + zs) * R3; // Rotation, not skew
                    xs = r - xs;
                    ys = r - ys;
                    zs = r - zs;
                }
                break;
            default:
                break;
        }

        int seed = mSeed;
        float amp = mDomainWarpAmp * mFractalBounding;
        float freq = mFrequency;

        for (int i = 0; i < mOctaves; i++) {
            DoSingleDomainWarp(seed, amp, freq, xs, ys, zs, coord);

            seed++;
            amp *= mGain;
            freq *= mLacunarity;
        }
    }
}
