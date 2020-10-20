from pathlib import Path
import importlib

from rlbottraining.common_exercises.bronze_goalie import BallRollingToGoalie as BronzeBallRollingToGoalie
from rlbottraining.common_exercises.silver_goalie import TryNotToOwnGoal
from rlbottraining.common_exercises.silver_striker import HookShot
from rlbottraining.common_exercises.dribbling import Dribbling
from rlbot.matchconfig.match_config import PlayerConfig, Team
import gold_goalie
from gold_goalie import GoldBallRollingToGoalie

from importlib import reload
import os

def get_shot():
    import custom_shot
    
    return custom_shot.CustomHookShot('Try')

get_shot()

def make_default_playlist():
    exercises =  [
        get_shot()
    ]

    for exercise in exercises:
        exercise.match_config.player_configs = [
            PlayerConfig.bot_config(
                Path(__file__).absolute().parent.parent / 'YangBot' / 'src' / 'main' / 'python' / 'yangbot.cfg', 
                Team.BLUE
            )
        ]

    return exercises