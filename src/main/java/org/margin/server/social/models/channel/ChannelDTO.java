package org.margin.server.social.models.channel;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChannelDTO {
    private Long id;
    private String name;
    private String description;
    private Long spaceId;

    public ChannelDTO(Channel channel) {
        this.id = channel.getId();
        this.name = channel.getName();
        this.description = channel.getDescription();
        this.spaceId = channel.getSpace().getId();
    }
}
