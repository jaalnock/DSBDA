package com.mapreduce.music;



import java.io.IOException;


import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

public class MusicTrackAnalysis {
	public static class MusicConstants{
		public static final int USER_ID = 0;
		public static final int TRACK_ID = 1;
		public static final int IS_SHARED = 2;
		public static final int RADIO = 3;
		public static final int IS_SKIPPED = 4;
	}
	public static class MusicMapper extends Mapper<LongWritable , Text , Text , Text>{
		private Text trackId = new Text();
		private Text data = new Text();
		
		@Override
		protected void map(LongWritable key , Text value , Context context) throws IOException , InterruptedException{
			String[] line = value.toString().split(",");
			if(line.length == 5) {
				String track = line[MusicConstants.TRACK_ID].trim();
				String radio = line[MusicConstants.RADIO].trim();
				String isSkipped = line[MusicConstants.IS_SKIPPED].trim();
				
				trackId.set(track);
				if(radio.equals("radio")) {
					data.set("R:1");
					context.write(trackId, data);
				}
				if("1".equals(isSkipped)) {
					data.set("S:1");
					context.write(trackId, data);
				}
			}
		}
	}
	
	public static class MusicReducer extends Reducer<Text , Text , Text , Text>{
		@Override
		protected void reduce(Text key , Iterable<Text>values , Context context) throws IOException , InterruptedException{
			int totalSkips = 0;
			int radioCount = 0;
			for(Text value : values) {
				String val = value.toString();
				if(val.startsWith("R:")) {
					radioCount += Integer.parseInt(val.substring(2));
				}
				else if(val.startsWith("S:")) {
					totalSkips += 1;
				}
			}
			String output = radioCount + "," + totalSkips;
			context.write(key, new Text(output));
		}
	}
	
	public static void main(String[] args) throws Exception {
		Configuration conf = new Configuration();
		Job job = Job.getInstance(conf , "Music Track Analysis");
		job.setJarByClass(MusicTrackAnalysis.class);
		job.setMapperClass(MusicMapper.class);
		job.setReducerClass(MusicReducer.class);
		job.setMapOutputKeyClass(Text.class);
		job.setMapOutputValueClass(Text.class);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(Text.class);
		FileInputFormat.addInputPath(job, new Path(args[0]));
		FileOutputFormat.setOutputPath(job, new Path(args[1]));
		System.exit(job.waitForCompletion(true) ? 0 : 1);
	}
}
