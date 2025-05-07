package log;

import java.io.IOException;

import java.text.ParseException;
import java.text.SimpleDateFormat;
//import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

public class LoginDuration {

    // Mapper: Parse CSV, compute login duration, emit <mac_address, duration>
    public static class DurationMapper extends Mapper<LongWritable, Text, Text, LongWritable> {
        private Text macAddress = new Text();
        private LongWritable duration = new LongWritable();
        private SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm");

        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String[] fields = value.toString().split(",");
            if (fields.length < 8) return; // Skip invalid lines

            String mac = fields[0].trim();
            String loginTimeStr = fields[5].trim();
            String logoutTimeStr = fields[7].trim();

            try {
                Date loginTime = dateFormat.parse(loginTimeStr);
                Date logoutTime = dateFormat.parse(logoutTimeStr);
                long diffInMillis = logoutTime.getTime() - loginTime.getTime();
                long diffInSeconds = diffInMillis / 1000;

                if (diffInSeconds >= 0) { // Valid duration
                    macAddress.set(mac);
                    duration.set(diffInSeconds);
                    context.write(macAddress, duration);
                }
            } catch (ParseException e) {
                // Skip malformed dates
            }
        }
    }

    // Reducer: Sum durations, find max, output users with max duration
    public static class MaxDurationReducer extends Reducer<Text, LongWritable, Text, Text> {
        private long maxDuration = Long.MIN_VALUE;
        private StringBuilder maxUsers = new StringBuilder();

        @Override
        protected void reduce(Text key, Iterable<LongWritable> values, Context context) throws IOException, InterruptedException {
            long totalDuration = 0;
            for (LongWritable val : values) {
                totalDuration += val.get();
            }

            // Update max duration and users
            if (totalDuration > maxDuration) {
                maxDuration = totalDuration;
                maxUsers.setLength(0);
                maxUsers.append(key.toString());
            } else if (totalDuration == maxDuration) {
                maxUsers.append(",").append(key.toString());
            }
        }

        @Override
        protected void cleanup(Context context) throws IOException, InterruptedException {
            // Convert maxDuration to hours for readability
            double hours = maxDuration / 3600.0;
//            String output = String.format("%.2f hours", hours);
            String output = hours + " hours";
            context.write(new Text(maxUsers.toString()), new Text(output));
        }
    }

    // Main: Configure and run the MapReduce job
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: LoginDuration <input path> <output path>");
            System.exit(1);
        }

        Configuration conf = new Configuration();
        Job job = Job.getInstance(conf, "Max Login Duration");
        job.setJarByClass(LoginDuration.class);

        // Set Mapper and Reducer classes
        job.setMapperClass(DurationMapper.class);
        job.setReducerClass(MaxDurationReducer.class);

        // Set output key and value types
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(LongWritable.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);

        // Set input and output paths from command-line arguments
        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));

        // Run the job
        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }
}
