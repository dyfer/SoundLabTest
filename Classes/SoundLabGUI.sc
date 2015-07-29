SoundLabGUI {
	// copyArgs
	var <sl, <wwwPort;
	var <slhw, <deviceAddr, <wsGUI;
	var <decsHoriz, <decsSphere, <decsDome, <discreteRouters, <decsMatrix;
	var <sampleRates, <stereoPending, <rotatePending;
	var <gainTxt, <gainSl, <muteButton, <attButton, <srMenu, <decMenus,
	<decMenuLayouts, <horizMenu, <sphereMenu, <domeMenu, <matrixMenu,
	<discreteMenu, <stereoMenu, <rotateMenu, <correctionCbAttributes, <correctionPuAttributes, <applyButton, <basicBalanceButton, <kernelMatchStatusTxt, // <correctionMenu,
	<stateTxt, <postTxt, <sweetSl, <sweetTxt, <sweetSpec, <kernelLayout, <kernelCheckBoxes, <kernelDegreeMenus;
	var <pendingDecType, <pendingInput, <pendingSR, <pendingKernel;

	var <ampSpec, <oscFuncDict, buildList;
	var <>maxPostLines, <postString, font = 'Helvetica', lgFontSize = 24, mdFontSize = 20, smFontSize = 16 ;

	//don't change web port unless you know how to change redirection in Apache web server
	*new { |soundLabModel, webInterfacePort = 8080|
		^super.newCopyArgs(soundLabModel, webInterfacePort).init;
	}

	init {
		var cond;
		cond = Condition(false);
		fork {
			"INITIALIZING WEB WINDOW".postln;
			sl.addDependant( this );
			if( sl.usingSLHW, {
				slhw = sl.slhw.addDependant(this)
			});

			" ---------- \n creating new instance of wsWindow \n------------".postln;
			wsGUI = WsWindow("Sound Lab Router", isDefault: true, wwwPort: wwwPort, suppressPosting: false);
			"\n".postln;
			1.5.wait; // TODO: add condition

			/*
			build arrays of the decoder/router synth names
			as specified in the config file
			*/
			decsHoriz = [];
			decsSphere = [];
			decsDome = [];
			discreteRouters = [];
			decsMatrix = [];

			sl.decAttributeList.do{ |dAtts|

				dAtts.postln;

				if( dAtts[1] == \discrete, {
					discreteRouters = discreteRouters.add(dAtts.first);
				},{
					switch( dAtts[3], // numDimensions
						2, { decsHoriz = decsHoriz.add(dAtts.first) },
						3, {
							if( dAtts[1] == \dome )
							{ decsDome = decsDome.add(dAtts.first) }
							{ decsSphere = decsSphere.add(dAtts.first) };
						}
					)
				});
			};

			decsMatrix = sl.matrixDecoderNames;

			"\n--decsHoriz--".postln; decsHoriz.do(_.postln);
			"\n--decsSphere--".postln; decsSphere.do(_.postln);
			"\n--decsDome--".postln; decsDome.do(_.postln);
			"\n--discreteRouters--".postln; discreteRouters.do(_.postln);
			"\n--decsMatrix--".postln; decsMatrix.do(_.postln);

			ampSpec = ControlSpec.new(-80, 12, -2, default: 0);
			// sweet spot diam (m)
			sweetSpec = ControlSpec.new(0.17, 4, 'lin', default: 0.5);
			sampleRates = [44100, 48000, 96000];
			maxPostLines = 12;
			postString = "";
			this.initVars(cond);
			cond.wait;
			"initializing controls".postln;
			this.initControls;
			"just before building controls".postln;
			this.buildControls;
		}
	}

	initControls {

		/* GAIN */
		gainTxt = WsStaticText.init(wsGUI, Rect(0,0,1,0.05))
		.string_("").font_(Font(font, mdFontSize))
		;

		gainSl = WsEZSlider.init(wsGUI)
		.controlSpec_(ampSpec)
		.action_({|sldr|
			sl.amp_( sldr.value )})
		;

		/* MUTE / ATTENUATE */
		muteButton = WsButton.init(wsGUI)
		.states_([
			["Mute", Color.black, Color.fromHexString("#FAFAFA")],
			["Muted", Color.white, Color.fromHexString("#F78181")]
		])
		.action_({ |but|
			switch( but.value,
				0, {sl.mute(false)}, 1, {sl.mute(true)}
			)
		})
		;

		attButton = WsButton.init(wsGUI)
		.states_([
			["Attenuate", Color.black, Color.fromHexString("#FAFAFA")],
			["Attenuated", Color.white, Color.fromHexString("#E2A9F3")]
		])
		.action_({ |but|
			switch( but.value,
				0, {sl.attenuate(false)}, 1, {sl.attenuate(true)}
			)
		})
		;

		/* SWEET SPOT DIAMETER */
		sweetTxt = WsStaticText.init(wsGUI, Rect(0,0,1,0.05))
		.string_("").font_(Font(font, mdFontSize))
		;
		sweetSl = WsEZSlider.init(wsGUI)
		.controlSpec_(sweetSpec)
		.action_({|sldr|
			sl.sweetSpotDiam_( sldr.value )
		})
		;

		/* SAMPLE RATE */
		srMenu = WsPopUpMenu.init(wsGUI)
		.items_(['-']++ sampleRates.collect(_.asSymbol))
		.action_({ |mn|
			(mn.value==0).if(
				{ pendingSR = nil },
				{
					(mn.item == sl.sampleRate.asSymbol).if({
						this.status_("Requested samplerate is already current.");
						mn.valueAction_(0); // set back to '-'
					},{	pendingSR = mn.item.asInt }
					)
				}
			)
		})
		;

		/* DECODER */
		decMenus = Dictionary();

		(decsHoriz.size > 0).if{
			decMenus.put( \horiz,
				IdentityDictionary(know: true)
				.put( \menu, horizMenu = WsPopUpMenu.init(wsGUI).items_(['-'] ++ decsHoriz) )
				.put( \label, "2-D Horizontal")
			);
		};
		(decsSphere.size > 0).if{
			decMenus.put( \sphere,
				IdentityDictionary(know: true)
				.put( \menu, sphereMenu = WsPopUpMenu.init(wsGUI).items_(['-'] ++ decsSphere) )
				.put( \label, "3-D Sphere" )
			);
		};
		(decsDome.size > 0).if{
			decMenus.put( \dome,
				IdentityDictionary(know: true)
				.put( \menu, domeMenu = WsPopUpMenu.init(wsGUI).items_(['-'] ++ decsDome) )
				.put( \label, "3-D Dome" )
			);
		};
		(decsMatrix.size > 0).if{
			decMenus.put( \matrix,
				IdentityDictionary(know: true)
				.put( \menu, matrixMenu = WsPopUpMenu.init(wsGUI).items_(['-'] ++ decsMatrix) )
				.put( \label, "Custom Matrix" )
			);
		};

		decMenuLayouts = [];
		[\horiz, \sphere, \dome, \matrix].do{|key|
			decMenus[key].notNil.if{
				decMenuLayouts = decMenuLayouts.add(
					WsHLayout( nil,
						WsStaticText.init(wsGUI).string_(
							decMenus[key].label).font_(Font(font, mdFontSize)
					), decMenus[key].menu )
				)
			}
		};

		decMenus.keysValuesDo{|k,v|
			decMenus[k].menu.action_({|mn|
				this.clearDecSelections(k); // sets pendingDecType to nil
				discreteMenu.value_(0);		// reset discrete routing menu
				pendingDecType = if(mn.item != '-', {mn.item},{nil});
			})
		};

		/* DISCRETE ROUTING */
		discreteMenu = WsPopUpMenu.init(wsGUI)
		.items_(['-'] ++ discreteRouters)
		.action_({|mn|
			this.clearDecSelections(); // sets pendingDecType to nil
			pendingDecType = if(mn.item != '-', {mn.item},{nil});
		})
		;

		/* STEREO / ROTATE */
		stereoMenu = WsPopUpMenu.init(wsGUI)
		.items_(['-','yes','no'])
		.action_({|mn|
			stereoPending = switch(mn.item,
				'-',{nil},'yes',{\on},'no',{\off}
			)
		})
		;
		rotateMenu = WsPopUpMenu.init(wsGUI)
		.items_(['-','yes','no'])
		.action_({|mn|
			rotatePending = switch(mn.item,
				'-',{nil},'yes',{\on},'no',{\off}
			)
		})
		;


		/* CORRECTION */

		correctionCbAttributes = sl.collectKernelCheckBoxAttributes;

		correctionPuAttributes = sl.collectKernelPopUpAttributes;

		kernelDegreeMenus = correctionPuAttributes.collect{ |atts|
			WsPopUpMenu.init(wsGUI)
			.items_(["-"]++atts)
			.action_({ this.respondToKernelSelection })
		}
		;

		basicBalanceButton = WsButton.init(wsGUI)
		.states_([
			["Basic Balance", Color.black, Color.white],
			["Basic Balance Pending", Color.black, Color.gray],
		])
		.action_({ |but|
			if( but.value == 1, {
				kernelCheckBoxes.do(_.value_(false)); // zero out check boxes
				kernelDegreeMenus.do(_.value_(0)); // deselect kernel menus
				pendingKernel = \basic_balance;
				this.kernelStatus_("Balance the speaker array, no digital room correction selected.");
			},{
				pendingKernel = nil;
				this.kernelStatus_("No pending kernel change.");
			}
			);
		})
		;

		kernelMatchStatusTxt = WsStaticText.init(wsGUI)
		.string_("No pending kernel change.").font_(Font(font, smFontSize))
		;

		/* APPLY */
		applyButton = WsSimpleButton.init(wsGUI)
		.string_("Apply")
		.action_({
			fork {
				block { |break|
					var updateCond;
					if( sl.usingSLHW,
						{
							if( slhw.audioIsRunning.not, {
								this.status_("Warning: Audio is stopped in Hardware");
								break.("Audio is currently stopped in the Hardware.".warn)
							});
						},{
							if( sl.server.serverRunning.not, {
								this.status_("Warning: Server is stopped");
								break.("Server is currently stopped.".warn)
							});
						}
					);
					// Anything to update?
					if( pendingDecType.isNil 	and:
						pendingSR.isNil			and:
						pendingKernel.isNil		and:
						stereoPending.isNil		and:
						rotatePending.isNil,
						{this.status_("No updates."); break.()}
					);

					this.status_( "Updating..." );
					updateCond = Condition(false);

					/* Update Decoder/Kernel */
					if( pendingDecType.notNil 	or:
						pendingKernel.notNil	or:
						pendingSR.notNil,
						{
							var sendKernelPath;
							// TODO check here if there's a SR change, if so set state current vars
							// of soundlab then change sample rate straight away,
							// no need to update signal chain twice

							pendingDecType = pendingDecType ?? sl.curDecoderPatch.decoderName;
							("Updating decoder to "++pendingDecType).postln;

							// find the kernel
							if( pendingKernel.notNil, {

								if(pendingKernel == \basic_balance,
									{ sendKernelPath = \basic_balance },
									{
										var selectedAttributes, result;
										// get check box states
										selectedAttributes = correctionCbAttributes.select{ |att, i|
											postf("% = %\n", att, kernelCheckBoxes[i].value);
											kernelCheckBoxes[i].value.asBoolean;
										};
										// get pop up states
										kernelDegreeMenus.do{ |menu|
											if(menu.item.asSymbol != '-',
												selectedAttributes = selectedAttributes.add(menu.item))
										};

										// find kernel match to selections
										result = sl.getKernelAttributesMatch(selectedAttributes);

										case
										{ result.isKindOf(String) }{
											pendingKernel = result }
										{ result == 0 }	{
											var msg = "Kernel will not be updated! No kernel found matching the selected attributes";
											msg.warn;
											this.status_( msg );
											this.kernelStatus_( "Kernel not updated. See status window." );

											pendingKernel = nil;
										}
										{ result == -1 }{
											var msg =  "Kernel will not be updated! More than one kernel found matching the selected attributes, edit the config so that kernel attributes are unique";
											msg.warn;
											this.status_( msg );
											this.kernelStatus_( "Kernel not updated. See status window." );

											pendingKernel = nil;
										};

										pendingKernel !? {
											sendKernelPath = sl.checkKernelFolder(pendingKernel);
											sendKernelPath ?? {
												this.status_("Folder for this kernel doesn't exist. Check that the folder specified in the config is available at all sample rates.")
											};
										};

									}
								);
							});

							// sendKernelPath of nil designates no kernel change
							sl.startNewSignalChain( pendingDecType, sendKernelPath, updateCond );

						},{ updateCond.test_(true).signal }
					);

					updateCond.wait; // wait for new signal chain to play

					/* Update Stereo routing */
					stereoPending !? {
						switch( stereoPending,
							\on,	{sl.stereoRouting_(true)},
							\off,	{sl.stereoRouting_(false)}
						)
					};

					/* Update listening position rotation */
					rotatePending !? {
						switch( rotatePending,
							\on, {
								if( sl.curDecoderPatch.attributes.kind == \discrete, {
									this.status_("Rotation not available for discrete routing".warn);
									rotateMenu.valueAction_(0);
								},{ sl.rotate_(true) }
								)
							},
							\off, { sl.rotate_(false) }
						)
					};

					/* Update Samplerate */
					if( pendingSR.notNil,
						{
							this.status_(format("Changing the sample rate to %.\nAudio will stop for a time while the routing system reboots to the new sample rate.", pendingSR));
							sl.sampleRate_( pendingSR ) },
						{ this.status_("Update complete.") }
					);
				}
			}
		})
		;

		/* STATE */
		stateTxt = WsStaticText.init(wsGUI).string_("").font_(Font(font, mdFontSize))
		;
		/* POST WINDOW */
		postTxt = WsStaticText.init(wsGUI).string_("").font_(Font(font, smFontSize))
		;
	}


	/* Updating the GUI when SoundLab model is changed */

	update {
		| who, what ... args |
		if( who == sl, {
			switch ( what,
				\amp,	{ var val;
					val  = args[0].ampdb;
					gainSl.value_(val);
					gainTxt.string_(format("<strong>Gain: </strong>% dB",val.round(0.01).asString));
				},
				\attenuate,	{
					switch ( args[0],
						0, {
							attButton.value_(0);
							if( sl.isMuted.not, {
								this.status_("Amp restored.") },{ this.status_("Muted.")
							});
						},
						1, {
							attButton.value_(1);
							if( sl.isMuted.not,
								{ this.status_("Attenuated.") },
								{ this.status_("Muted, attenuated.") }
							);
						}
					)
				},
				\mute,	{
					switch ( args[0],
						0, {
							muteButton.value_(0);
							if( sl.isAttenuated.not,
								{ this.status_("Amp restored.") },
								{ this.status_("Attenuated.") }
							);
						},
						1, {
							muteButton.value_(1);
							if( sl.isAttenuated.not,
								{ this.status_("Muted.") },
								{ this.status_("Muted, attenuated.") }
							);
						}
					)
				},
				\sweetSpotDiam, { var val = args[0];
					sweetSl.value_( val );
					sweetTxt.string_( format(
						"<strong>Listening Area Diameter: </strong>% m",
						val.round(0.001).asString)
					);
				},
				\clipped,	{
					this.status_( (args[0] < sl.numHardwareIns).if(
						{ "Clipped IN " ++ args[0].asString },
						{ "Clipped OUT " ++ (args[0]-sl.numHardwareIns).asString }
					));
				},
				\decoder,	{
					this.clearDecSelections;	// sets pending decoder to nil
					discreteMenu.value_(0);		// reset discrete routing menu
					this.status_("Now decoding with: " ++ sl.curDecoderPatch.decoderName);
				},
				\stereo,	{
					this.status_( args[0].if(
						{"Stereo added to first two output channels."},{"Stereo cleared."}
					));
					stereoMenu.valueAction_(0);
				},
				\rotate,	{
					var rotated;
					rotated = args[0];
					this.status_( rotated.if(
						{"Soundfield rotated."},{"Rotation cleared."}
					));
					rotateMenu.valueAction_(0);
				},
				\kernel,	{
					kernelCheckBoxes.do(_.value_(false));	// clear the kernel boxes
					kernelDegreeMenus.do(_.value_(0));		// set degree menu to the first selection
					basicBalanceButton.value_(0);

					this.kernelStatus_("Now running: " ++ sl.formatKernelStatePost(sl.curKernel).asString);
					this.status_("Kernel updated: " ++ sl.formatKernelStatePost(sl.curKernel).asString);
				},
				\stateLoaded,	{
					this.initVars;
					this.recallValues;
					this.status_("State reloaded. Confirm in Current System Settings window.")
				},
				\stoppingAudio, { this.status_("Audio is stopping - Standby.") },
				\reportError,	{ this.status_(args[0]) },
				\reportStatus,	{ this.status_(args[0]) }
			);
			this.postState;
		});
		if( who == slhw, {
			switch( what,
				\audioIsRunning, { args[0].not.if(
					{ this.status_("Audio stopped. Cannot update at this time.") }
				);
				},
				\stoppingAudio, { this.status_("Audio is stopping - Please wait...") }
			)
		});
	}

	respondToKernelSelection {
		var selectedAttributes, result, msg;

		// get check box states
		selectedAttributes = correctionCbAttributes.select{ |att, i|
			kernelCheckBoxes[i].value.asBoolean;
		};

		// get pop up states
		kernelDegreeMenus.do{ |menu|
			if( menu.item.asSymbol != '-', {
				selectedAttributes = selectedAttributes.add(menu.item)
			});
		};

		// set pendingKernel
		(selectedAttributes.size == 0).if(
			{
				pendingKernel = nil;
				msg = "No kernel change selected."
			},{
				basicBalanceButton.value_(0);

				// set pending kernel to not Nil though not yet a valid kernel
				pendingKernel = ("match pending - " ++ selectedAttributes);

				result = sl.getKernelAttributesMatch(selectedAttributes);

				msg = case
				{result.isKindOf(String)} { "Found kernel match." }
				{result == 0}  {"No kernel found matching selected criteria."}
				{result == -1} {"More than one kernel found, refine selected criteria."}
				;
		});

		this.kernelStatus_(msg);
	}

	status_ { |aString|
		var newLines, curLines, curAndNewLines, newPost;
		curLines = postString.split($\n);
		newLines = ( Date.getDate.format("%a %m/%d %I:%M:%S")++"\t"++ aString).split($\n);
		curAndNewLines = (curLines ++ newLines);
		if((curLines.size + newLines.size) > maxPostLines, {
			var stripNum;
			stripNum = (curLines.size + newLines.size) - maxPostLines;
			curAndNewLines = curAndNewLines.drop(stripNum);
		});
		newPost = "";
		curAndNewLines.do{ |line, i|
			newPost = newPost ++ line ++ "\n";
		};
		postTxt.string_(newPost);
		postString = newPost;
	}

	kernelStatus_ { |aString|
		kernelMatchStatusTxt.string_(aString)
	}

	postState {
		stateTxt.string_("<strong>"
			++ "\t" ++ sl.sampleRate ++ "\n "
			++ "\t" ++ sl.stereoActive.if({"YES"},{"NO"}) ++ "\n "
			++ "\t" ++ sl.rotated.if({"YES"},{"NO"}) ++ "\n "
			++ "\t" ++ sl.curDecoderPatch.decoderName ++ "\n "
			++ "\t" ++ sl.formatKernelStatePost(sl.curKernel).asString
			++ "</strong>"
		);
	}

	clearDecSelections {|exceptThisKey|
		decMenus.keysValuesDo{|k,v|
			if(k != exceptThisKey,{v.menu.value_(0)}) }
		;
		pendingDecType = nil;
	}


	/* PAGE LAYOUT */

	buildControls {

		wsGUI.layout_(
			WsVLayout( Rect(0.025,0.025,0.95,0.95),

				WsStaticText.init(wsGUI, Rect(0,0,1,0.1)).string_(
					format("<strong>%\nRouting and Decoding System</strong>",sl.labName))
				.align_(\center).font_(Font(font, lgFontSize)),

				WsHLayout( Rect(0,0,1,0.9),

					// COLUMN 1
					WsVLayout( Rect(0,0,0.45,1),

						/* current settings */
						WsHLayout( Rect(0,0,1, 0.08),
							WsStaticText.init(wsGUI, Rect(0,0,1,1)).string_(
								"<strong>Current System Settings</strong>")
							.align_(\center).font_(Font(font, mdFontSize))
							.background_(Color.fromHexString("#FFFFCC")),
						),

						WsHLayout( Rect(0,0,1, 0.3),
							WsStaticText.init(wsGUI, Rect(0,0,0.5,1)).string_(
								"Sample Rate: \nStereo channels first: \nSound field rotated: \nDecoder / Router: \nCorrection: \n"
							)
							.align_(\right)
							.font_(Font(font, mdFontSize))
							.background_(Color.fromHexString("#FFFFCC")),

							stateTxt.bounds_(Rect(0,0,0.5,1))
							.background_(Color.fromHexString("#FFFFCC")),
						),
						0.05,

						/* amp controls */
						WsVLayout( Rect(0,0,1,0.15),
							gainTxt.bounds_(Rect(0,0,1,1/3)),
							gainSl.bounds_(Rect(0,0,2/3,1/3)),

							/* mute / attenuate */
							WsHLayout( Rect(0,0,0.5,1/3),
								muteButton, 0.15, attButton
							)
						),
						0.05,

						/* shelf freq - sweet spot size */
						WsVLayout( Rect(0,0,1,0.12),
							sweetTxt.bounds_(Rect(0,0,1,1/2)),
							sweetSl.bounds_(Rect(0,0,2/3,1/2))
						),
						0.05,

						/* post window */
						WsStaticText.init(wsGUI, Rect(0,0,1,0.04))
						.string_("<strong>Post:</strong>").font_(Font(font, mdFontSize)),

						postTxt.bounds_(Rect(0,0,1,0.5))
						.background_(Color.fromHexString("#F2F2F2"))
					),


					// COLUMN 2
					WsVLayout( Rect(0,0,0.05,1),
						// space
					),

					// COLUMN 3
					WsVLayout( Rect(0,0,0.45,1),

						/* Change Settings title */
						WsStaticText.init(wsGUI, Rect(0,0,1,0.1)).string_(
							"<strong>Change Settings</strong>"
						).align_(\center).font_(Font(font, mdFontSize)),

						/* Sample Rate / Stereo layout */
						WsHLayout( Rect(0,0,1,0.15),

							/* stereo */
							WsVLayout( Rect(0,0,0.6,1),

								WsStaticText.init(wsGUI, Rect(0,0, 1,0.65))
								.string_(
									"<strong>Insert STEREO channels before decoder/router?</strong>"
								)
								.align_(\left)
								.font_(Font(font, mdFontSize)),

								WsHLayout( Rect(0,0, 1, 0.35), stereoMenu, 2/3)
							),
							0.05,

							/* sample rate */
							WsVLayout( Rect(0,0,0.4,1),
								WsStaticText.init(wsGUI, Rect(0,0,1,1))
								.string_("<strong>Sample Rate</strong>")
								.font_(Font(font, mdFontSize)),
								srMenu.bounds_(Rect(0,0,0.5,1)), 0.05,
							)
						),
						0.05,

						// DECODER / ROUTER selection
						WsHLayout( Rect(0,0,1,0.6),

							/* ambisonic decoder */
							WsVLayout( Rect(0,0,0.6, 1),

								WsVLayout( Rect(0,0, 1, 0.2),
									WsStaticText.init(wsGUI,Rect(0,0,1,0.08))
									.string_("<strong>Select an Ambisonic Decoder</strong>")
									.align_(\left).font_(Font(font, mdFontSize))
								),
								WsVLayout( Rect(0,0, 1, 0.8),
									*decMenuLayouts ++
									[
										0.1,
										WsStaticText.init(wsGUI, Rect(0,0,1,0.15))
										.string_( format(
											"Rotate listening position % degrees?",
											sl.rotateDegree))
										.align_(\left)
										.font_(Font(font, mdFontSize)),
										0.05
									] ++
									WsHLayout( nil, rotateMenu, 2/3)
								),
							),
							0.05,

							/* discrete routing */
							WsVLayout( Rect(0,0,0.4, 1),

								WsStaticText.init(wsGUI, Rect(0,0,1,0.2))
								.string_("<strong>Select a Discrete Routing Layout</strong>")
								.align_(\left)
								.font_(Font(font, mdFontSize)),
								0.05,

								WsStaticText.init(wsGUI, Rect(0,0,1,0.15))
								.string_("Which speakers?")
								.align_(\left)
								.font_(Font(font, mdFontSize)),
								0.05,
								WsHLayout( Rect(0,0, 1,0.1),
									discreteMenu.bounds_(Rect(0,0,1/2,1)), 0.5),
								0.45 // push "which speakers?" and discrete menu together
							)
						),
						nil,

						// ROOM CORRECTION
						WsVLayout( Rect(0,0,1,0.2),

							WsStaticText.init(wsGUI, Rect(0,0, 1,0.3))
							.string_("<strong>Room correction</strong>")
							.align_(\left)
							.font_(Font(font, mdFontSize)),

							WsHLayout( Rect(0,0,1, 0.8),

								kernelLayout = WsHLayout(
									Rect( 0,0,
										correctionCbAttributes.size / (kernelDegreeMenus.size + correctionCbAttributes.size),
										1),
									*{
										kernelCheckBoxes = correctionCbAttributes.collect{ |att|
											WsCheckbox.init(wsGUI, nil)
										};

										// return the VLayouts from this function
										correctionCbAttributes.collect{ |att, i|
											WsVLayout( Rect(0,0,1,1),
												kernelCheckBoxes[i],
												WsStaticText.init(wsGUI, nil).string_(att.asString),
											);
										};
									}.value
								),
								0.05,

								WsHLayout(
									Rect(0,0,
										kernelDegreeMenus.size / (kernelDegreeMenus.size + correctionCbAttributes.size),
										1),
									*kernelDegreeMenus.collect(_.bounds_( Rect(0,0.0,1,0.5) ))
								),
								0.05,
								WsStaticText.init(wsGUI, nil).string_("\n-or-").align_('center'),
								0.05,
								basicBalanceButton.bounds_(Rect(0,0,0.2, 0.8)),
							),
							kernelMatchStatusTxt
						),

						0.05,

						// apply button
						WsHLayout( Rect(0,0,1,0.05),
							2/3, applyButton)
					)
				)
			);
		);
		"BUILT CONTROLS".postln;
		this.recallValues;				// this will turn on the defaults
		"Interface built.".postln;
	}

	initVars { |loadCondition|
		pendingDecType = nil;
		pendingInput = nil;
		pendingSR = nil;
		stereoPending = nil;
		rotatePending = nil;

		postf("\tCurrent decoder: %\n\tCurrent SR: %\n\tCurKernel: %\n",
			sl.curDecoderPatch.decoderName, sl.sampleRate, sl.curKernel);
		loadCondition !? {loadCondition.test_(true).signal}
	}

	recallValues {
		fork {
			var sweetRad, kernelCheck;
			gainSl.value_(sl.globalAmp.ampdb);
			gainTxt.string_( format(
				"<strong>Gain: </strong>% dB",
				sl.globalAmp.ampdb.round(0.01))
			);

			muteButton.value_( if(sl.isMuted, {1},{0}) );
			attButton.value_( if(sl.isAttenuated, {1},{0}) );

			/* set/refresh kernel CheckBoxes */
			kernelLayout.remove;
			kernelLayout = WsHLayout(
				kernelLayout.bounds,		// bounds are still preserved despite it being removed
				*{
					kernelCheckBoxes = correctionCbAttributes.collect{ |att|
						WsCheckbox.init(wsGUI, Rect(0,0,1,1))
						// action definition below
					};

					// return the VLayouts from this function
					correctionCbAttributes.collect{ |att, i|
						WsVLayout( Rect(0,0,1,1),
							WsStaticText.init(wsGUI, nil).string_(att.asString),
							kernelCheckBoxes[i]
						);
					};
				}.value // evalutating this fuction returns the checkbox's VLayouts
			);

			// put the new layout back in the old one's place
			wsGUI.layout_(kernelLayout);

			// on account of WsCheckbox bug, action needs to be set after it's laid out
			correctionCbAttributes.do{ |att, i|
				kernelCheckBoxes[i].action_({ |cb|
					// de-select basic balance
					cb.value.asBoolean.if{ basicBalanceButton.value_(0) };

					this.respondToKernelSelection;
				})
			};
			basicBalanceButton.value_(0);
			kernelDegreeMenus.do(_.value_(0));

			sweetRad = sl.getDiamByFreq(sl.shelfFreq.round(0.001));
			sweetSl.value_(sweetRad);
			sweetTxt.string_( format(
				"<strong>Listening Area Diameter: </strong>% m",
				sweetRad.round(0.001))
			);

			(	decMenus.values.collect({|dict| dict.menu})
				++ [srMenu, discreteMenu, stereoMenu, rotateMenu]
			).do{ |menu| menu.value_(0)};

			this.postState;
		}
	}

	cleanup {
		sl.removeDependant( this );
		slhw !? {slhw.removeDependant(this)};
		wsGUI.free;
	}

}

/* TESTING
l = SoundLab(48000, loadGUI:true, useSLHW: false, useKernels: false, configFileName: "CONFIG_117.scd",usingOSX: true)
l = SoundLab(48000, loadGUI:true, useSLHW: false, useKernels: false, configFileName: "CONFIG_TEST_205.scd", usingOSX: true)

l.cleanup
s.quit

s.options.numOutputBusChannels

x = 4.collect{|i| { Out.ar(l.curDecoderPatch.inbusnum+i,PinkNoise.ar(0.75)* SinOsc.kr(0.2*(i+1)).range(0.1,1))}.play}
x.do(_.free)
s.meter
*/