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

public class Movie {

    // Mapper class: Emits <movieId, rating> for each input record
    public static class MovieMapper extends Mapper<LongWritable, Text, Text, FloatWritable> {
        private Text movieId = new Text();
        private FloatWritable rating = new FloatWritable();

        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            // Skip header or malformed rows
            if (key.get() == 0 && value.toString().contains("userId")) return;

            String[] fields = value.toString().split(",");
            if (fields.length != 4) return;

            try {
                movieId.set(fields[1].trim());                      // movieId
                rating.set(Float.parseFloat(fields[2].trim()));    // rating
                context.write(movieId, rating);                    // Emit <movieId, rating>
            } catch (NumberFormatException e) {
                // Ignore lines with invalid number formats
            }
        }
    }

    // Reducer class: Computes average rating per movie and recommends top-rated movies
    public static class MovieReducer extends Reducer<Text, FloatWritable, Text, FloatWritable> {
        private TreeMap<Float, String> topMovies = new TreeMap<>(); // Sorted by rating
        private float maxRating = Float.MIN_VALUE;
        private Text highestRatedMovie = new Text();

        @Override
        protected void reduce(Text key, Iterable<FloatWritable> values, Context context) throws IOException, InterruptedException {
            float total = 0;
            int count = 0;

            for (FloatWritable val : values) {
                total += val.get();
                count++;
            }

            float avgRating = total / count;
            topMovies.put(avgRating, key.toString());

            if (avgRating > maxRating) {
                maxRating = avgRating;
                highestRatedMovie.set(key);
            }
        }

        @Override
        protected void cleanup(Context context) throws IOException, InterruptedException {
            // Output highest rated movie
            context.write(new Text("Movie with Highest Rating:"), null);
            context.write(highestRatedMovie, new FloatWritable(maxRating));

            // Output top 10 movies in descending order of rating
            context.write(new Text("\nTop 10 Recommended Movies:"), null);
            int count = 0;
            for (Map.Entry<Float, String> entry : topMovies.descendingMap().entrySet()) {
                context.write(new Text(entry.getValue()), new FloatWritable(entry.getKey()));
                count++;
                if (count >= 10) break;
            }
        }
    }

    // Driver class: Sets up and runs the MapReduce job
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: Movie <input path> <output path>");
            System.exit(-1);
        }

        Configuration conf = new Configuration();
        Job job = Job.getInstance(conf, "Movie Recommendation");

        job.setJarByClass(Movie.class);
        job.setMapperClass(MovieMapper.class);
        job.setReducerClass(MovieReducer.class);

        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(FloatWritable.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(FloatWritable.class);

        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));

        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }
}
