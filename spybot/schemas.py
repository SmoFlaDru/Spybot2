from typing import TypedDict


class TSChannelSchema(TypedDict):
    id: int
    name: str


class TSUserSchema(TypedDict):
    name: str
    channel_id: int


class LiveResponse(TypedDict):
    clients: list[TSUserSchema]
    channels: list[TSChannelSchema]


class UserChannelSchema(TypedDict):
    channel_id: int
    name: str
