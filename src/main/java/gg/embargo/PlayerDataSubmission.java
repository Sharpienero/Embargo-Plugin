package gg.embargo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PlayerDataSubmission
{
    private String username;
    private String profile;
    private PlayerData data;
}