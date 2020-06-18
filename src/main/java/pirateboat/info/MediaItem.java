package pirateboat.info;

import lombok.Data;

@Data
public class MediaItem {

    public String title;
    public String originalTitle;
    public MediaType type;
    public int year;

}
