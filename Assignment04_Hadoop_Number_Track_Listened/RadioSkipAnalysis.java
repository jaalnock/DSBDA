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

public class RadioSkipAnalysis {

    public static class RadioSkipMapper extends Mapper<LongWritable, Text, Text, Text> {
        private Text trackId = new Text();
        private Text metric = new Text();

        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            // Skip header
            if (key.get() == 0 && value.toString().contains("UserId")) return;

            String[] fields = value.toString().split(",");
            if (fields.length != 5) return;

            String track = fields[1].trim();
            String radio = fields[3].trim();
            String skip = fields[4].trim();

            try {
                trackId.set(track);

                if (Integer.parseInt(radio) == 1) {
                    metric.set("R");  // Radio count
                    context.write(trackId, metric);
                }
                if (Integer.parseInt(skip) == 1) {
                    metric.set("K");  // Skip count
                    context.write(trackId, metric);
                }
            } catch (NumberFormatException e) {
                // Skip malformed entries
            }
        }
    }

    public static class RadioSkipReducer extends Reducer<Text, Text, Text, Text> {
        @Override
        protected void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
            int radioCount = 0;
            int skipCount = 0;

            for (Text val : values) {
                if (val.toString().equals("R")) {
                    radioCount++;
                } else if (val.toString().equals("K")) {
                    skipCount++;
                }
            }

            String output = String.format("%d,%d", radioCount, skipCount);
            context.write(key, new Text(output));  // Format: TrackID    radioCount,skipCount
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: RadioSkipAnalysis <input path> <output path>");
            System.exit(1);
        }

        Configuration conf = new Configuration();
        Job job = Job.getInstance(conf, "Radio and Skip Analysis");

        job.setJarByClass(RadioSkipAnalysis.class);
        job.setMapperClass(RadioSkipMapper.class);
        job.setReducerClass(RadioSkipReducer.class);

        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(Text.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);

        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));

        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }
}
