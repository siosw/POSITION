/*

simple_SERVER.sc

- uses two sources for each access point (connected system)
- control azimuth and elevation of each source via OSC

- needs SC-HOA library installed

Henrik von Coler
2021-06-09

*/


/////////////////////////////////////////////////////////////////////////////////
// Server & Setup
/////////////////////////////////////////////////////////////////////////////////

// some server parameters
s.options.device               = "sprawl_SERVER";
s.options.numInputBusChannels  = 32;
s.options.numOutputBusChannels = 2;
s.options.maxLogins            = 4;
s.options.bindAddress          = "0.0.0.0";


// maximum number of access points to be used
~nSystems  = 16;

// number of in/out channels per access point (and jacktrip connection)
~nChannels = 2;

// two sources for each system
~nSources  = ~nChannels*~nSystems;

// HOA parameters
~hoa_order      = 3;
~n_hoa_channels = pow(~hoa_order + 1.0 ,2.0).asInteger;

// kill all other servers
Server.killAll;

s.boot;

s.waitForBoot({

	/////////////////////////////////////////////////////////////////////////////////
	// Synthdefs: 3rd oder encoder and decoder
	/////////////////////////////////////////////////////////////////////////////////

	SynthDef(\hoa_mono_encoder,
		{
			|
			in_bus     = nil, // audio input bus index
			out_bus    = nil, // audio output bus index
			//
			azim       = 0,
			elev       = 0,
			dist       = 1
			|

			var sound = In.ar(in_bus);
			var level = (0.75/(max(0,dist)+1.0));
			var bform = HOASphericalHarmonics.coefN3D(~hoa_order, azim, elev) * sound * level;

			Out.ar(out_bus, bform);

	}).add;

	// load HOA stuff for binaural decoder
	HOABinaural.loadbinauralIRs(s);
	s.sync;

	SynthDef(\hoa_binaural_decoder,
		{
			|
			in_bus  = nil, // audio input bus index
			out_bus = nil  // audio output bus index
			|

			var sig = HOABinaural.ar(~hoa_order, In.ar(in_bus,~n_hoa_channels));
			Out.ar(out_bus, sig);

	}).add;
	s.sync;


	/////////////////////////////////////////////////////////////////////////////////
	// Encoders & Decoder
	/////////////////////////////////////////////////////////////////////////////////

	// audio bus for the encoded ambisonics signal
	~ambi_BUS      = Bus.audio(s, ~n_hoa_channels);

	// group for all encoders
	~encoder_GROUP = Group.new(s);
	s.sync;

	// add an encoder for each source
	~binaural_encoders = Array.fill(~nSources,	{arg i;

		Synth(\hoa_mono_encoder,
			[
				\in_bus,     s.options.numOutputBusChannels + i,
				\out_bus,    ~ambi_BUS.index,
			],
			target: ~encoder_GROUP)
	});
	s.sync;

	// add one decoder after the encoder group
	~decoder = Synth.after(~encoder_GROUP, \hoa_binaural_decoder,
		[
			\in_bus,  ~ambi_BUS.index,
			\out_bus, 0,
	]);
	s.sync;


	/////////////////////////////////////////////////////////////////////////////////
	// Control
	/////////////////////////////////////////////////////////////////////////////////

	// create control buses for angle parameters
	~azim_BUS = Bus.control(s, ~nSources);
	~elev_BUS = Bus.control(s, ~nSources);
	~dist_BUS = Bus.control(s, ~nSources);

	// map buses to encoder parameters
	~binaural_encoders.do({arg e, i; e.map(\azim, ~azim_BUS.index+i)});
	~binaural_encoders.do({arg e, i; e.map(\elev, ~elev_BUS.index+i)});
	~binaural_encoders.do({arg e, i; e.map(\dist, ~dist_BUS.index+i)});

	thisProcess.openUDPPort(57121);
	"listening for OSC on ports: ".post;
	thisProcess.openPorts.postln;

	// OSC listener for azimuth
	OSCdef('azim',
		{
			arg msg, time, addr, recvPort;
			var azim = msg[2];
			~azim_BUS.setAt(msg[1], azim);
	    }, '/source/azim');

	// OSC listener for elevation<
	OSCdef('elev',
		{
			arg msg, time, addr, recvPort;
			var elev = msg[2];
			~elev_BUS.setAt(msg[1], elev);
	    }, '/source/elev');

	// OSC listener for distance
	OSCdef('dist',
		{
			arg msg, time, addr, recvPort;
			var dist = msg[2];
			~dist_BUS.setAt(msg[1], dist);
	    }, '/source/dist');

	///////////////////////////////////////////////////////////////////////a//////////
	// Broadcast
	/////////////////////////////////////////////////////////////////////////////////
	~broadcast_receiver = NetAddr("127.0.0.1", 5005);

	~broadcast_interval = 0.01;

	~broadcast_OSC_ROUTINE = Routine({
		inf.do({
			var azim, elev, dist;
			for (0, ~nSources-1, {
			 	arg i;

			 	azim = ~azim_BUS.getnSynchronous(~nInputs)[i];
			 	elev = ~elev_BUS.getnSynchronous(~nInputs)[i];
			 	dist = ~dist_BUS.getnSynchronous(~nInputs)[i];

				~broadcast_receiver.sendMsg('/source/aed', i, azim, elev, dist);
			});
			~broadcast_interval.wait;
		});
	});

	~broadcast_OSC_ROUTINE.play;

});
