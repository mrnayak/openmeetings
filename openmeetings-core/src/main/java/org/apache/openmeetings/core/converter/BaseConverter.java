/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License") +  you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.openmeetings.core.converter;

import static org.apache.openmeetings.core.data.record.listener.async.BaseStreamWriter.TIME_TO_WAIT_FOR_FRAME;
import static org.apache.openmeetings.util.OmFileHelper.EXTENSION_FLV;
import static org.apache.openmeetings.util.OmFileHelper.EXTENSION_JPG;
import static org.apache.openmeetings.util.OmFileHelper.getRecordingMetaData;
import static org.apache.openmeetings.util.OmFileHelper.getStreamsSubDir;
import static org.apache.openmeetings.util.OpenmeetingsVariables.CONFIG_IMAGEMAGIC_PATH;
import static org.apache.openmeetings.util.OpenmeetingsVariables.webAppRootKey;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.directory.api.util.Strings;
import org.apache.openmeetings.db.dao.basic.ConfigurationDao;
import org.apache.openmeetings.db.dao.record.RecordingMetaDataDao;
import org.apache.openmeetings.db.dao.record.RecordingMetaDeltaDao;
import org.apache.openmeetings.db.entity.record.Recording;
import org.apache.openmeetings.db.entity.record.RecordingMetaData;
import org.apache.openmeetings.db.entity.record.RecordingMetaData.Status;
import org.apache.openmeetings.db.entity.record.RecordingMetaDelta;
import org.apache.openmeetings.util.OmFileHelper;
import org.apache.openmeetings.util.process.ConverterProcessResult;
import org.apache.openmeetings.util.process.ProcessHelper;
import org.red5.io.flv.impl.FLVWriter;
import org.red5.logging.Red5LoggerFactory;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class BaseConverter {
	private static final Logger log = Red5LoggerFactory.getLogger(BaseConverter.class, webAppRootKey);
	private static final Pattern p = Pattern.compile("\\d{2,5}(x)\\d{2,5}");
	public final static String EXEC_EXT = System.getProperty("os.name").toUpperCase().indexOf("WINDOWS") < 0 ? "" : ".exe";

	@Autowired
	private ConfigurationDao configurationDao;
	@Autowired
	private RecordingMetaDataDao metaDataDao;
	@Autowired
	private RecordingMetaDeltaDao metaDeltaDao;

	protected static class Dimension {
		public int width = 0;
		public int height = 0;

		public Dimension(int width, int height) {
			this.width = width;
			this.height = height;
		}
	}

	private String getPath(String key, String app) {
		String path = configurationDao.getConfValue(key, String.class, "");
		if (!Strings.isEmpty(path) && !path.endsWith(File.separator)) {
			path += File.separator;
		}
		path += app;
		return path;
	}

	public String getPathToFFMPEG() {
		return getPath("ffmpeg_path", "ffmpeg");
	}

	protected String getPathToSoX() {
		return getPath("sox_path", "sox");
	}

	protected String getPathToConvert() {
		return getPath(CONFIG_IMAGEMAGIC_PATH, "convert") + EXEC_EXT;
	}

	protected String getPathToIdentify() {
		return getPath(CONFIG_IMAGEMAGIC_PATH, "identify") + EXEC_EXT;
	}

	protected File getStreamFolder(Recording recording) {
		return getStreamsSubDir(recording.getRoomId());
	}

	protected long diff(Date from, Date to) {
		return from.getTime() - to.getTime();
	}

	protected double diffSeconds(Date from, Date to) {
		return diffSeconds(diff(from, to));
	}

	protected double diffSeconds(long val) {
		return ((double)val) / 1000;
	}

	protected String formatMillis(long millis) {
		long hours = TimeUnit.MILLISECONDS.toHours(millis);
		millis -= TimeUnit.HOURS.toMillis(hours);
		long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
		millis -= TimeUnit.MINUTES.toMillis(minutes);
		long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);
		millis -= TimeUnit.SECONDS.toMillis(seconds);
		return String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, millis);
	}

	protected void updateDuration(Recording r) {
		r.setDuration(formatMillis(diff(r.getRecordEnd(), r.getRecordStart())));
	}

	protected void deleteFileIfExists(File f) {
		if (f.exists()) {
			f.delete();
		}
	}

	protected String[] mergeAudioToWaves(List<File> waveFiles, File wav) throws IOException {
		List<String> argv = new ArrayList<>();

		argv.add(getPathToSoX());
		argv.add("-m");
		for (File arg : waveFiles) {
			argv.add(arg.getCanonicalPath());
		}
		argv.add(wav.getCanonicalPath());

		return argv.toArray(new String[0]);
	}

	protected void stripAudioFirstPass(Recording recording, List<ConverterProcessResult> returnLog,
			List<File> waveFiles, File streamFolder)
	{
		stripAudioFirstPass(recording, returnLog, waveFiles, streamFolder
				, metaDataDao.getAudioMetaDataByRecording(recording.getId()));
	}

	private String[] addSoxPad(List<ConverterProcessResult> returnLog, String job, double length, double position, File inFile, File outFile) throws IOException {
		//FIXME need to check this
		if (length < 0 || position < 0) {
			log.debug("::addSoxPad " + job + " Invalid parameters: "
					+ " length = " + length + "; position = " + position + "; inFile = " + inFile);
		}
		length = length < 0 ? 0 : length;
		position = position < 0 ? 0 : position;

		String[] argv = new String[] { getPathToSoX(), inFile.getCanonicalPath(), outFile.getCanonicalPath(), "pad", "" + length, "" + position };

		returnLog.add(ProcessHelper.executeScript(job, argv));
		return argv;
	}

	private static File getMetaFlvSer(RecordingMetaData metaData) {
		File metaDir = getStreamsSubDir(metaData.getRecording().getRoomId());
		return new File(metaDir, metaData.getStreamName() + ".flv.ser");
	}

	public static void printMetaInfo(RecordingMetaData metaData, String prefix) {
		if (log.isDebugEnabled()) {
			log.debug(String.format("### %s:: recording id %s; stream with id %s; current status: %s ", prefix, metaData.getRecording().getId()
					, metaData.getId(), metaData.getStreamStatus()));
			File metaFlv = getRecordingMetaData(metaData.getRecording().getRoomId(), metaData.getStreamName());
			File metaSer = getMetaFlvSer(metaData);
			log.debug(String.format("### %s:: Flv file [%s] exists ? %s; size: %s, lastModified: %s ", prefix, metaFlv.getPath(), metaFlv.exists(), metaFlv.length(), metaFlv.lastModified()));
			log.debug(String.format("### %s:: Ser file [%s] exists ? %s; size: %s, lastModified: %s ", prefix, metaSer.getPath(), metaSer.exists(), metaSer.length(), metaSer.lastModified()));
		}
	}

	protected RecordingMetaData waitForTheStream(long metaId) throws InterruptedException {
		RecordingMetaData metaData = metaDataDao.get(metaId);
		if (metaData.getStreamStatus() != Status.STOPPED) {
			log.debug("### meta Stream not yet written to disk " + metaId);
			long counter = 0;
			long maxTimestamp = 0;
			while(true) {
				log.trace("### Stream not yet written Thread Sleep - " + metaId);

				metaData = metaDataDao.get(metaId);

				if (metaData.getStreamStatus() == Status.STOPPED) {
					printMetaInfo(metaData, "Stream now written");
					log.debug("### Thread continue ... " );
					break;
				} else {
					File metaFlv = getRecordingMetaData(metaData.getRecording().getRoomId(), metaData.getStreamName());
					if (metaFlv.exists() && maxTimestamp < metaFlv.lastModified()) {
						maxTimestamp = metaFlv.lastModified();
					}
					File metaSer = getMetaFlvSer(metaData);
					if (metaSer.exists() && maxTimestamp < metaSer.lastModified()) {
						maxTimestamp = metaSer.lastModified();
					}
					if (maxTimestamp + TIME_TO_WAIT_FOR_FRAME < System.currentTimeMillis()) {
						if (metaSer.exists()) {
							log.debug("### long time without any update, trying to repair ... ");
							try {
								if (FLVWriter.repair(metaSer.getCanonicalPath(), null, null) && !metaSer.exists()) {
									log.debug("### Repairing was successful ... ");
								} else {
									log.debug("### Repairing was NOT successful ... ");
								}
							} catch (IOException e) {
								log.error("### Error while file repairing ... ", e);
							}
						} else {
							log.debug("### long time without any update, closing ... ");
						}
						metaData.setStreamStatus(Status.STOPPED);
						metaDataDao.update(metaData);
						break;
					}
				}
				if (++counter % 1000 == 0) {
					printMetaInfo(metaData, "Still waiting");
				}

				Thread.sleep(100L);
			}
		}
		return metaData;
	}

	protected void stripAudioFirstPass(Recording recording,
			List<ConverterProcessResult> returnLog,
			List<File> waveFiles, File streamFolder,
			List<RecordingMetaData> metaDataList) {
		try {
			// Init variables
			log.debug("### meta Data Number - " + metaDataList.size());
			log.debug("###################################################");

			for (RecordingMetaData metaData : metaDataList) {
				long metaId = metaData.getId();
				log.debug("### processing metadata: " + metaId);
				if (metaData.getStreamStatus() == Status.NONE) {
					log.debug("Stream has not been started, error in recording " + metaId);
					continue;
				}

				metaData = waitForTheStream(metaId);

				File inputFlvFile = new File(streamFolder, OmFileHelper.getName(metaData.getStreamName(), EXTENSION_FLV));

				File outputWav = new File(streamFolder, metaData.getStreamName() + "_WAVE.wav");

				metaData.setWavAudioData(outputWav.getName());

				log.debug("FLV File Name: {} Length: {} ", inputFlvFile.getName(), inputFlvFile.length());

				if (inputFlvFile.exists()) {
					String[] argv = new String[] {
							getPathToFFMPEG(), "-y"
							, "-i", inputFlvFile.getCanonicalPath()
							, "-af", "aresample=32k:min_comp=0.001:min_hard_comp=0.100000"
							, outputWav.getCanonicalPath()};

					returnLog.add(ProcessHelper.executeScript("stripAudioFromFLVs", argv));
				}

				if (outputWav.exists() && outputWav.length() != 0) {
					metaData.setAudioValid(true);
					// Strip Wave to Full Length
					File outputGapFullWav = outputWav;

					// Fix Start/End in Audio
					List<RecordingMetaDelta> metaDeltas = metaDeltaDao.getByMetaId(metaId);

					int counter = 0;

					for (RecordingMetaDelta metaDelta : metaDeltas) {
						File inputFile = outputGapFullWav;

						// Strip Wave to Full Length
						String hashFileGapsFullName = metaData.getStreamName() + "_GAP_FULL_WAVE_" + counter + ".wav";
						outputGapFullWav = new File(streamFolder, hashFileGapsFullName);

						metaDelta.setWaveOutPutName(hashFileGapsFullName);

						String[] argv_sox = null;

						if (metaDelta.getDeltaTime() != null) {
							double gapSeconds = diffSeconds(metaDelta.getDeltaTime());
							if (metaDelta.isStartPadding()) {
								argv_sox = addSoxPad(returnLog, "fillGap", gapSeconds, 0, inputFile, outputGapFullWav);
							} else if (metaDelta.isEndPadding()) {
								argv_sox = addSoxPad(returnLog, "fillGap", 0, gapSeconds, inputFile, outputGapFullWav);
							}
						}

						if (argv_sox != null) {
							log.debug("START fillGap ################# Delta-ID :: " + metaDelta.getId());

							metaDeltaDao.update(metaDelta);
							counter++;
						} else {
							outputGapFullWav = inputFile;
						}
					}

					// Strip Wave to Full Length
					String hashFileFullName = metaData.getStreamName() + "_FULL_WAVE.wav";
					File outputFullWav = new File(streamFolder, hashFileFullName);

					// Calculate delta at beginning
					double startPad = diffSeconds(metaData.getRecordStart(), recording.getRecordStart());

					// Calculate delta at ending
					double endPad = diffSeconds(recording.getRecordEnd(), metaData.getRecordEnd());

					addSoxPad(returnLog, "addStartEndToAudio", startPad, endPad, outputGapFullWav, outputFullWav);

					// Fix for Audio Length - Invalid Audio Length in Recorded Files
					// Audio must match 100% the Video
					log.debug("############################################");
					log.debug("Trim Audio to Full Length -- Start");

					if (!outputFullWav.exists()) {
						throw new Exception("Audio File does not exist , could not extract the Audio correctly");
					}
					metaData.setFullWavAudioData(hashFileFullName);

					// Finally add it to the row!
					waveFiles.add(outputFullWav);
				}

				metaDataDao.update(metaData);
			}
		} catch (Exception err) {
			log.error("[stripAudioFirstPass]", err);
		}
	}

	protected String getDimensions(Recording r) {
		return String.format("%sx%s", r.getWidth(), r.getHeight());
	}

	protected List<String> addMp4OutParams(Recording r, List<String> argv, String mp4path) {
		argv.addAll(Arrays.asList(
				"-c:v", "h264", //
				"-crf", "24",
				"-pix_fmt", "yuv420p",
				"-preset", "medium",
				"-profile:v", "baseline",
				"-c:a", "libfaac",
				"-c:a", "libfdk_aac",
				"-ar", "22050",
				"-b:a", "32k", //FIXME add quality constants
				"-s", getDimensions(r), //
				mp4path
				));
		return argv;
	}

	protected String convertToMp4(Recording r, List<String> _argv, List<ConverterProcessResult> returnLog) throws IOException {
		//TODO add faststart, move filepaths to helpers
		String mp4path = r.getFile().getCanonicalPath();
		List<String> argv = new ArrayList<>(Arrays.asList(getPathToFFMPEG(), "-y"));
		argv.addAll(_argv);
		returnLog.add(ProcessHelper.executeScript("generate MP4", addMp4OutParams(r, argv, mp4path).toArray(new String[]{})));
		return mp4path;
	}

	protected void convertToJpg(Recording r, String mp4path, List<ConverterProcessResult> returnLog) throws IOException {
		// Extract first Image for preview purpose
		// ffmpeg -i movie.flv -vcodec mjpeg -vframes 1 -an -f rawvideo -s
		// 320x240 movie.jpg
		File jpg = r.getFile(EXTENSION_JPG);
		String[] argv = new String[] { //
				getPathToFFMPEG(), "-y", //
				"-i", mp4path, //
				"-vcodec", "mjpeg", //
				"-vframes", "100", "-an", //
				"-f", "rawvideo", //
				"-s", getDimensions(r), //
				jpg.getCanonicalPath() };

		returnLog.add(ProcessHelper.executeScript("generate preview JPG", argv));
	}

	protected static Dimension getDimension(String txt) {
		Matcher matcher = p.matcher(txt);

		while (matcher.find()) {
			String foundResolution = txt.substring(matcher.start(), matcher.end());
			String[] resultions = foundResolution.split("x");
			return new Dimension(Integer.valueOf(resultions[0]).intValue(), Integer.valueOf(resultions[1]).intValue());
		}

		return new Dimension(100, 100); // will return 100x100 for non-video to be able to play
	}
}
