package movie;

import java.awt.geom.Line2D;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.FloatWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

public class Movie{
	public static class MapperClass extends Mapper<LongWritable , Text , Text , FloatWritable>{
		@Override
		protected void map(LongWritable key , Text value , Context context) throws IOException , InterruptedException{
			String[] line = value.toString().split(",");
			if(key.get() == 0) {
				return;
			}
			
			Text outputKey = new Text(line[1]);
			FloatWritable outputValue = new FloatWritable(Float.parseFloat(line[2]));
			
			context.write(outputKey , outputValue);
		}
	}
	
	
	public static class ReducerClass extends Reducer<Text , FloatWritable , Text , FloatWritable>{
		Text maxRated = new Text();
		float maxRating = Float.MIN_VALUE;
		private TreeMap<Float , String> mp = new TreeMap<>();
		
		@Override
		protected void reduce(Text key , Iterable<FloatWritable> values , Context context) throws IOException , InterruptedException{
			float sum = 0.0f;
			float cnt = 0.0f;
			
			for(FloatWritable val : values) {
				sum += val.get();
				cnt++;
			}
			
			sum = (sum / cnt);
			
			mp.put(sum , key.toString());
			
			if(sum > maxRating) {
				maxRating = sum;
				maxRated.set(key);
			}
		}
		
		@Override
		protected void cleanup(Context context) throws IOException , InterruptedException{
			context.write(new Text("Movie with highest rating : ") , null);
			context.write(maxRated, new FloatWritable(maxRating));
			
			int count = 0;
			context.write(new Text("\nMoive in sorted order : ") , null);
			
			for(Map.Entry<Float , String> entry : mp.descendingMap().entrySet()) {
				context.write(new Text(entry.getValue()) , new FloatWritable(entry.getKey()));
				count++;
				if(count >= 10) {
					break;
				}
			}
		}
	}
	
	public static void main(String[] args) throws Exception {
		Configuration conf = new Configuration();
		Job job = Job.getInstance(conf , "Movie Rating");
		job.setJarByClass(Movie.class);
		job.setMapperClass(MapperClass.class);
		job.setReducerClass(ReducerClass.class);
		job.setMapOutputKeyClass(Text.class);
		job.setMapOutputValueClass(FloatWritable.class);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(FloatWritable.class);
		FileInputFormat.addInputPath(job , new Path(args[0]));
		FileOutputFormat.setOutputPath(job, new Path(args[1]));
		System.exit(job.waitForCompletion(true) ? 0 : 1);
	}
 }