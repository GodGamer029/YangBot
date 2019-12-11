from pathlib import Path
import importlib

from rlbottraining.common_exercises.bronze_goalie import BallRollingToGoalie as BronzeBallRollingToGoalie
from rlbottraining.common_exercises.silver_goalie import TryNotToOwnGoal
from rlbot.matchconfig.match_config import PlayerConfig, Team
import gold_goalie
from gold_goalie import GoldBallRollingToGoalie
def make_default_playlist():
    exercises =  [
        GoldBallRollingToGoalie('Gold')
    ]

    for exercise in exercises:
        exercise.match_config.player_configs = [
            PlayerConfig.bot_config(
                Path(__file__).absolute().parent.parent / 'YangBot' / 'src' / 'main' / 'python' / 'yangbot.cfg', 
                Team.BLUE
            )
        ]

    return exercises