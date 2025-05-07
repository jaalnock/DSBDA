package music;


import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
//import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

public class MusicAnalysis {
	public static class MusicMapper extends Mapper<LongWritable , Text , Text , Text>{
		private Text trackId = new Text();
		private Text value1 = new Text();
		
		@Override
		protected void map(LongWritable key, Text value , Context context) throws IOException , InterruptedException{
			if(key.get() == 0 && value.toString().contains("UserId")) return;
			
			String[]fields = value.toString().split(",");
			if(fields.length != 5) return;
			
			String track = fields[1].trim();
            String userId = fields[0].trim();
            String shared = fields[2].trim();
            
            try {
            	Integer.parseInt(track);
            	Integer.parseInt(userId);
            	int sharedVal = Integer.parseInt(shared);
            	if(sharedVal != 0 && sharedVal != 1) return;
            }
            catch (NumberFormatException e) {
				// TODO: handle exception
            	return;
			}
            
            trackId.set(track);
            
            value1.set("U:" + userId);
            context.write(trackId , value1);
            
            value1.set("S:" + shared);
            context.write(trackId, value1);
		}
	}
	
	public static class MusicReducer extends Reducer<Text , Text , Text , Text>{
		@Override
		protected void reduce(Text key , Iterable<Text>values , Context context)throws IOException , InterruptedException{
			Set<String> uniqueUsers = new HashSet<>();
			int totalShares = 0;
			
			for(Text val : values) {
				String value = val.toString();
				if(value.startsWith("U:")) {
					uniqueUsers.add(value.substring(2));
				}else if( value.startsWith("S:")) {
					try {
						totalShares += Integer.parseInt(value.substring(2));
					}
					catch (NumberFormatException e) {
						// TODO: handle exception
					}
				}
			}
			
			String output = String.format("%d,%d" , uniqueUsers.size(), totalShares);
			context.write(key , new Text(output));
		}
	}
	
	public static void main(String[] args) throws Exception{
		if (args.length != 2) {
            System.err.println("Usage: MusicAnalytics <input path> <output path>");
            System.exit(1);
        }
		
		Configuration conf = new Configuration();
		Job job = Job.getInstance(conf , "Music Analysis");
		
		job.setJarByClass(MusicAnalysis.class);
		
		job.setMapperClass(MusicMapper.class);
		job.setReducerClass(MusicReducer.class);
		
		job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(Text.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);
        
        FileInputFormat.addInputPath(job, new Path(args[0]));
//        FileOutputFormat.setOutputPath(job, new Path(args[1]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));
        
        System.exit(job.waitForCompletion(true) ? 0 : 1);
	}
}
